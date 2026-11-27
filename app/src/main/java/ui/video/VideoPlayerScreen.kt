package ui.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.media.AudioManager
import android.os.Build
import android.view.SurfaceView
import android.view.TextureView
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbDownOffAlt
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.ThumbUpOffAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.MainActivity
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import data.download.VideoDownloadEligibility
import data.download.VideoDownloadEligibilityEvaluator
import data.online.OnlineTrackMapper
import data.video.OnlineVideoDataSource
import data.video.VideoRecommendationEngine
import data.video.VideoRecommendationFeedbackEntity
import domain.video.ChannelNotificationPreference
import domain.video.FullscreenSwipeDecision
import domain.video.OnlineVideoChannel
import domain.video.OnlineVideoItem
import domain.video.OnlineVideoPlaylistSummary
import domain.video.VideoAspectRatioCalculator
import domain.video.VideoAspectRatioMode
import domain.video.VideoGestureClassifier
import domain.video.VideoPinchZoomMath
import domain.video.VideoPinchZoomState
import domain.video.VideoPlaybackProgress
import domain.video.VideoPlayerUiState
import domain.video.VideoRatingState
import domain.video.VideoTouchZone
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import ui.theme.EditorialColors
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun formatVideoTime(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L) / 1000L)
    val hrs = totalSec / 3600L
    val mins = (totalSec % 3600L) / 60L
    val secs = totalSec % 60L
    return if (hrs > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%d:%02d", mins, secs)
    }
}

/**
 * Full-Screen & Portrait Video Player + Complete Video Details Screen for SONORA Online Video.
 * Supports Media3 TextureView rendering, landscape fullscreen rotation, PiP, gesture volume/brightness,
 * double-tap 10s seek, resolution/speed/subtitle/audio switching, SONORA Like/Dislike/Share/Save
 * interaction bar, local SONORA Subscribe + Notification Bell, description, and related videos.
 */
@Composable
fun VideoPlayerScreen(
    playerState: VideoPlayerUiState,
    watchLaterIds: Set<String>,
    savedVideoIds: Set<String>,
    videoPlaylists: List<OnlineVideoPlaylistSummary>,
    colors: EditorialColors,
    onAttachSurface: (TextureView) -> Unit,
    onDetachSurface: (TextureView) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onSelectStream: (domain.video.VideoStreamOption) -> Unit,
    onSelectAutoQuality: () -> Unit = {},
    onSelectAudio: (domain.video.VideoAudioOption) -> Unit,
    onSelectSubtitle: (domain.video.VideoSubtitleOption?) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onSelectAspectRatio: (VideoAspectRatioMode) -> Unit = {},
    onToggleFullScreen: (Boolean) -> Unit,
    onMinimize: () -> Unit,
    onRetry: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleDislike: () -> Unit,
    onToggleSubscribe: (OnlineVideoChannel) -> Unit,
    onSelectNotificationPref: (OnlineVideoChannel, ChannelNotificationPreference) -> Unit,
    onToggleWatchLater: (OnlineVideoItem) -> Unit,
    onToggleSaveVideo: (OnlineVideoItem) -> Unit,
    onAddVideoToPlaylist: (Long, String, OnlineVideoItem) -> Unit,
    onCreateVideoPlaylist: (String, OnlineVideoItem) -> Unit,
    onPlayRelatedVideo: (OnlineVideoItem, List<OnlineVideoItem>) -> Unit,
    onRemoveFromQueue: (String) -> Unit,
    onOpenChannel: (String, String) -> Unit,
    onVideoMoreClick: (OnlineVideoItem) -> Unit,
    onRequestDownloadVideo: ((OnlineVideoItem, domain.video.ResolvedVideoDetails?) -> Unit)? = null,
    recommendationFeedback: List<VideoRecommendationFeedbackEntity> = emptyList(),
    playbackProgressFlow: StateFlow<VideoPlaybackProgress>? = null,
    onAttachSurfaceView: ((SurfaceView) -> Unit)? = null,
    onDetachSurfaceView: ((SurfaceView) -> Unit)? = null,
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
    val details = playerState.resolvedDetails
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val gestureSettings = playerState.gestureSettings

    var controlsVisible by remember { mutableStateOf(true) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showAspectRatioSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showDescriptionExpanded by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showSaveSheet by remember { mutableStateOf(false) }
    var showNotificationSheet by remember { mutableStateOf(false) }
    var gestureHudText by remember { mutableStateOf<String?>(null) }
    var isScrubbing by remember { mutableStateOf(false) }

    // Live interactive feedback for central vertical swipe (fullscreen & swipe-down-to-mini-player)
    var swipeDragOffsetPx by remember { mutableFloatStateOf(0f) }
    var swipeDragProgress by remember { mutableFloatStateOf(0f) }
    var isCentralSwiping by remember { mutableStateOf(false) }
    var isSwipingDownToMini by remember { mutableStateOf(false) }

    // Pinch-to-zoom & two-finger pan state (reset cleanly per video)
    var pinchZoomState by remember(video.videoId) { mutableStateOf(VideoPinchZoomState()) }

    // Track manual vs sensor orientation transitions to prevent ping-pong loops
    var lastObservedOrientation by remember { mutableStateOf(configuration.orientation) }

    val channelModel = remember(video, details, playerState.isSubscribedLocally, playerState.notificationPreference) {
        val url = video.channelUrl ?: "https://www.youtube.com"
        val channelId = OnlineVideoDataSource.extractChannelIdFromUrl(video.channelUrl)
        OnlineVideoChannel(
            channelId = channelId.ifBlank { video.channelName },
            stableId = OnlineTrackMapper.computeStableId("youtube_channel", url),
            channelUrl = url,
            name = video.channelName,
            avatarUrl = details?.channelAvatarUrl ?: video.channelAvatarUrl,
            subscriberCount = details?.channelSubscriberCount,
            isSubscribedLocal = playerState.isSubscribedLocally,
            isSubscribedOnProvider = false,
            notificationPreference = playerState.notificationPreference
        )
    }

    // Back press in landscape fullscreen exits smoothly to portrait (never closes video)
    BackHandler(enabled = playerState.isFullScreenLandscape && !playerState.isInPictureInPicture) {
        controlsVisible = true
        onToggleFullScreen(false)
    }

    // Respond to manual device rotation when Automatic Screen Orientation is enabled
    LaunchedEffect(configuration.orientation, gestureSettings.autoOrientationEnabled, playerState.isInPictureInPicture) {
        val currentOrient = configuration.orientation
        if (currentOrient != lastObservedOrientation) {
            lastObservedOrientation = currentOrient
            if (gestureSettings.autoOrientationEnabled && !playerState.isInPictureInPicture) {
                when (currentOrient) {
                    Configuration.ORIENTATION_LANDSCAPE -> {
                        if (!playerState.isFullScreenLandscape) {
                            onToggleFullScreen(true)
                        }
                    }
                    Configuration.ORIENTATION_PORTRAIT -> {
                        if (playerState.isFullScreenLandscape) {
                            onToggleFullScreen(false)
                        }
                    }
                }
            }
        }
    }

    // Apply orientation, immersive system bars, and display cutout mode for API 26–36
    DisposableEffect(playerState.isFullScreenLandscape, gestureSettings.autoOrientationEnabled, activity) {
        val previousCutoutMode = if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity.window.attributes.layoutInDisplayCutoutMode
        } else {
            0
        }
        if (activity != null) {
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            if (playerState.isFullScreenLandscape) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val attrs = activity.window.attributes
                    attrs.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    activity.window.attributes = attrs
                }
            } else {
                activity.requestedOrientation = if (gestureSettings.autoOrientationEnabled) {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
                controller.show(WindowInsetsCompat.Type.systemBars())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val attrs = activity.window.attributes
                    attrs.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                    activity.window.attributes = attrs
                }
            }
        }
        onDispose {
            if (activity != null) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val attrs = activity.window.attributes
                    attrs.layoutInDisplayCutoutMode = previousCutoutMode
                    activity.window.attributes = attrs
                }
            }
        }
    }

    // Auto-hide controls after 3.5s while playing
    LaunchedEffect(controlsVisible, playerState.isPlaying, isScrubbing, showSettingsSheet, showAspectRatioSheet) {
        if (controlsVisible && playerState.isPlaying && !isScrubbing && !showSettingsSheet && !showAspectRatioSheet) {
            delay(3500L)
            controlsVisible = false
        }
    }

    LaunchedEffect(gestureHudText) {
        if (gestureHudText != null) {
            delay(1100L)
            gestureHudText = null
        }
    }

    val isFullScreen = playerState.isFullScreenLandscape || playerState.isInPictureInPicture

    // Latest reactive state holders for pointerInput without cancelling ongoing gestures on state change
    val latestIsFullScreenLandscape by rememberUpdatedState(playerState.isFullScreenLandscape)
    val latestGestureSettings by rememberUpdatedState(gestureSettings)
    val latestVideoExp by rememberUpdatedState(playerState.videoExperienceSettings)
    val latestPinchZoom by rememberUpdatedState(pinchZoomState)
    val latestBlockedByUi by rememberUpdatedState(
        isScrubbing || showSettingsSheet || showAspectRatioSheet || showQueueSheet ||
            showPlaylistDialog || showNotificationSheet || playerState.isInPictureInPicture ||
            !playerState.errorMessage.isNullOrBlank()
    )
    val latestOnToggleFullScreen by rememberUpdatedState(onToggleFullScreen)
    val latestOnMinimize by rememberUpdatedState(onMinimize)
    val latestOnSeekBy by rememberUpdatedState(onSeekBy)

    val animatedSwipeOffsetY by animateFloatAsState(
        targetValue = if (isCentralSwiping) swipeDragOffsetPx else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium),
        label = "video_central_swipe_offset"
    )
    val animatedSwipeScale by animateFloatAsState(
        targetValue = if (isCentralSwiping) {
            if (playerState.isFullScreenLandscape || isSwipingDownToMini) 1f - (swipeDragProgress * 0.08f)
            else 1f + (swipeDragProgress * 0.04f)
        } else {
            1f
        },
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium),
        label = "video_central_swipe_scale"
    )

    val targetPortraitContainerRatio = remember(
        playerState.aspectRatio,
        playerState.aspectRatioMode,
        gestureSettings.autoAspectRatioEnabled
    ) {
        VideoAspectRatioCalculator.computePortraitContainerAspectRatio(
            videoAspectRatio = playerState.aspectRatio,
            mode = playerState.aspectRatioMode,
            autoAspectRatioEnabled = gestureSettings.autoAspectRatioEnabled
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(if (isFullScreen) Color.Black else colors.background)
            .then(if (!isFullScreen) Modifier.statusBarsPadding() else Modifier)
            .testTag("video_player_screen")
    ) {
        val screenMaxHeight = maxHeight
        val screenMaxWidth = maxWidth
        val portraitVideoHeight = remember(screenMaxWidth, screenMaxHeight, targetPortraitContainerRatio) {
            val ideal = screenMaxWidth / targetPortraitContainerRatio.coerceAtLeast(0.5f)
            val maxAllowed = (screenMaxHeight * 0.56f).coerceAtLeast(210.dp)
            ideal.coerceIn(180.dp, maxAllowed)
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Adaptive Video Viewport (hardware-composited SurfaceView to eliminate TextureView GPU blit lag)
            BoxWithConstraints(
                modifier = (if (isFullScreen) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .height(portraitVideoHeight)
                })
                    .background(Color.Black)
                    .clipToBounds()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                if (!latestBlockedByUi) {
                                    controlsVisible = !controlsVisible
                                }
                            },
                            onDoubleTap = { offset ->
                                if (latestBlockedByUi) return@detectTapGestures
                                if (latestPinchZoom.isZoomed) {
                                    pinchZoomState = VideoPinchZoomMath.reset()
                                    gestureHudText = "Zoom reset (1.0×)"
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    return@detectTapGestures
                                }
                                val seekSec = latestGestureSettings.doubleTapSeekSeconds
                                val seekMs = latestGestureSettings.doubleTapSeekMs
                                if (offset.x < size.width / 2f) {
                                    latestOnSeekBy(-seekMs)
                                    gestureHudText = "-${seekSec}s"
                                } else {
                                    latestOnSeekBy(seekMs)
                                    gestureHudText = "+${seekSec}s"
                                }
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        )
                    }
                    .pointerInput(activity, audioManager, density) {
                        val touchSlopPx = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (latestBlockedByUi) return@awaitEachGesture

                            val vpW = size.width.toFloat()
                            val vpH = size.height.toFloat()
                            val zone = VideoGestureClassifier.classifyTouchZone(
                                startX = down.position.x,
                                startY = down.position.y,
                                viewportWidth = vpW,
                                viewportHeight = vpH
                            )
                            if (zone == VideoTouchZone.SYSTEM_EDGE_GUARD) {
                                return@awaitEachGesture
                            }

                            var totalDx = 0f
                            var totalDy = 0f
                            var verticalLocked = false
                            var horizontalRejected = false
                            var pinchActive = false
                            var gestureTriggered = false
                            var volumeAccumulator = 0f
                            val pointerId = down.id

                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (latestBlockedByUi) break
                                    val pressedCount = event.changes.count { it.pressed }
                                    if (pressedCount == 0) break

                                    // Multi-touch Pinch-to-Zoom + Two-Finger Pan in central viewport
                                    if (pressedCount >= 2 && latestVideoExp.pinchToZoomEnabled) {
                                        val zoomFactor = event.calculateZoom()
                                        val panOffset = event.calculatePan()
                                        if (zoomFactor != 1f || panOffset.x != 0f || panOffset.y != 0f) {
                                            pinchActive = true
                                            isCentralSwiping = false
                                            val updated = VideoPinchZoomMath.applyTransform(
                                                current = pinchZoomState,
                                                zoomChange = zoomFactor,
                                                panX = panOffset.x,
                                                panY = panOffset.y,
                                                viewportWidthPx = vpW,
                                                viewportHeightPx = vpH
                                            )
                                            pinchZoomState = updated
                                            gestureHudText = if (updated.isZoomed) {
                                                String.format(Locale.US, "Zoom %.1f×", updated.scale)
                                            } else {
                                                "Zoom 1.0×"
                                            }
                                            event.changes.forEach { it.consume() }
                                        }
                                        continue
                                    }
                                    if (pinchActive) continue

                                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                    if (!change.pressed) break

                                    val delta = change.positionChange()
                                    totalDx += delta.x
                                    totalDy += delta.y

                                    if (!verticalLocked && !horizontalRejected) {
                                        val absDx = abs(totalDx)
                                        val absDy = abs(totalDy)
                                        if (absDx > touchSlopPx && absDx >= absDy * 0.9f) {
                                            // Horizontal motion — do not trigger vertical fullscreen/brightness/volume
                                            horizontalRejected = true
                                            break
                                        }
                                        if (absDy > touchSlopPx && absDy > absDx * 1.35f) {
                                            verticalLocked = true
                                        }
                                    }

                                    if (verticalLocked && !horizontalRejected) {
                                        when (zone) {
                                            VideoTouchZone.LEFT_BRIGHTNESS -> {
                                                change.consume()
                                                val deltaFraction = -delta.y / vpH.coerceAtLeast(1f)
                                                activity?.window?.let { win ->
                                                    val lp = win.attributes
                                                    val current = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
                                                    val next = (current + deltaFraction * 1.35f).coerceIn(0.05f, 1.0f)
                                                    lp.screenBrightness = next
                                                    win.attributes = lp
                                                    gestureHudText = "Brightness ${(next * 100).toInt()}%"
                                                }
                                            }
                                            VideoTouchZone.RIGHT_VOLUME -> {
                                                change.consume()
                                                audioManager?.let { am ->
                                                    val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                                    val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                                                    volumeAccumulator += (-delta.y / vpH.coerceAtLeast(1f)) * maxVol.toFloat() * 1.4f
                                                    val step = volumeAccumulator.toInt()
                                                    if (step != 0) {
                                                        volumeAccumulator -= step
                                                        val nextVol = (curVol + step).coerceIn(0, maxVol)
                                                        am.setStreamVolume(AudioManager.STREAM_MUSIC, nextVol, 0)
                                                        gestureHudText = "Volume ${(nextVol * 100 / maxVol)}%"
                                                    }
                                                }
                                            }
                                            VideoTouchZone.CENTER_FULLSCREEN -> {
                                                val settings = latestGestureSettings
                                                val expSettings = latestVideoExp
                                                val inLandscape = latestIsFullScreenLandscape
                                                val draggingDownInPortrait = !inLandscape && totalDy > 0f && expSettings.inAppSwipeMiniPlayerEnabled
                                                if (!settings.swipeFullscreenEnabled && !draggingDownInPortrait) {
                                                    break
                                                }
                                                change.consume()
                                                val thresholdPx = with(density) { settings.swipeSensitivity.thresholdDp.dp.toPx() }
                                                val progress = if (draggingDownInPortrait) {
                                                    (totalDy / (thresholdPx * 1.15f).coerceAtLeast(1f)).coerceIn(0f, 1f)
                                                } else {
                                                    VideoGestureClassifier.computeDragProgress(
                                                        totalDx = totalDx,
                                                        totalDy = totalDy,
                                                        thresholdPx = thresholdPx,
                                                        isFullScreenLandscape = inLandscape
                                                    )
                                                }
                                                isCentralSwiping = true
                                                isSwipingDownToMini = draggingDownInPortrait
                                                swipeDragProgress = progress
                                                val maxVisualShift = with(density) { 42.dp.toPx() }
                                                swipeDragOffsetPx = if (inLandscape || draggingDownInPortrait) {
                                                    (progress * maxVisualShift).coerceIn(0f, maxVisualShift)
                                                } else {
                                                    (-progress * maxVisualShift).coerceIn(-maxVisualShift, 0f)
                                                }

                                                if (!gestureTriggered) {
                                                    when (
                                                        VideoGestureClassifier.evaluateVerticalFullscreenSwipe(
                                                            totalDx = totalDx,
                                                            totalDy = totalDy,
                                                            thresholdPx = thresholdPx,
                                                            isFullScreenLandscape = inLandscape,
                                                            isSwipeEnabled = settings.swipeFullscreenEnabled,
                                                            isBlockedByControlsOrMenu = latestBlockedByUi,
                                                            isSwipeDownToMiniPlayerEnabled = expSettings.inAppSwipeMiniPlayerEnabled
                                                        )
                                                    ) {
                                                        FullscreenSwipeDecision.ENTER_FULLSCREEN -> {
                                                            gestureTriggered = true
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            controlsVisible = false
                                                            gestureHudText = "Full screen"
                                                            latestOnToggleFullScreen(true)
                                                        }
                                                        FullscreenSwipeDecision.EXIT_FULLSCREEN -> {
                                                            gestureTriggered = true
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            controlsVisible = true
                                                            gestureHudText = "Portrait mode"
                                                            latestOnToggleFullScreen(false)
                                                        }
                                                        FullscreenSwipeDecision.MINIMIZE_TO_MINI_PLAYER -> {
                                                            gestureTriggered = true
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            latestOnMinimize()
                                                        }
                                                        FullscreenSwipeDecision.NONE -> Unit
                                                    }
                                                }
                                            }
                                            VideoTouchZone.SYSTEM_EDGE_GUARD -> break
                                        }
                                    }
                                }
                            } finally {
                                isCentralSwiping = false
                                isSwipingDownToMini = false
                                swipeDragProgress = 0f
                                swipeDragOffsetPx = 0f
                            }
                        }
                    }
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
                    .testTag("video_surface_container"),
                contentAlignment = Alignment.Center
            ) {
                val vpWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                val vpHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                val effectiveRatioMode = if (
                    playerState.aspectRatioMode == VideoAspectRatioMode.AUTO &&
                    !gestureSettings.autoAspectRatioEnabled
                ) {
                    VideoAspectRatioMode.FIT
                } else {
                    playerState.aspectRatioMode
                }

                val surfaceLayout = remember(
                    vpWidthPx,
                    vpHeightPx,
                    playerState.videoWidth,
                    playerState.videoHeight,
                    playerState.pixelWidthHeightRatio,
                    playerState.unappliedRotationDegrees,
                    effectiveRatioMode
                ) {
                    VideoAspectRatioCalculator.computeSurfaceLayout(
                        viewportWidthPx = vpWidthPx,
                        viewportHeightPx = vpHeightPx,
                        videoWidth = playerState.videoWidth,
                        videoHeight = playerState.videoHeight,
                        pixelWidthHeightRatio = playerState.pixelWidthHeightRatio,
                        unappliedRotationDegrees = playerState.unappliedRotationDegrees,
                        mode = effectiveRatioMode
                    )
                }

                val frameW = with(density) { surfaceLayout.frameWidthPx.toDp() }
                val frameH = with(density) { surfaceLayout.frameHeightPx.toDp() }
                val baseSurfaceW = with(density) { surfaceLayout.surfaceWidthPx.toDp() }
                val baseSurfaceH = with(density) { surfaceLayout.surfaceHeightPx.toDp() }

                // Isolated hardware-composited video viewport (no per-frame TextureView GPU blit or spring layout churn)
                VideoSurfaceViewport(
                    frameWidth = frameW,
                    frameHeight = frameH,
                    surfaceWidth = baseSurfaceW,
                    surfaceHeight = baseSurfaceH,
                    zoomScale = animatedSwipeScale * pinchZoomState.scale,
                    offsetX = pinchZoomState.offsetX,
                    offsetY = animatedSwipeOffsetY + pinchZoomState.offsetY,
                    onAttachSurfaceView = onAttachSurfaceView,
                    onDetachSurfaceView = onDetachSurfaceView,
                    onAttachTextureView = onAttachSurface,
                    onDetachTextureView = onDetachSurface
                )

                // Subtle central swipe drag indicator
                androidx.compose.animation.AnimatedVisibility(
                    visible = isCentralSwiping && swipeDragProgress > 0.12f,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(if (playerState.isFullScreenLandscape) Alignment.TopCenter else Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.Black.copy(alpha = 0.72f),
                        border = BorderStroke(0.75.dp, Color.White.copy(alpha = 0.25f)),
                        modifier = Modifier.testTag("video_swipe_fullscreen_hint")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (playerState.isFullScreenLandscape || isSwipingDownToMini) {
                                    Icons.Rounded.KeyboardArrowDown
                                } else {
                                    Icons.Rounded.KeyboardArrowUp
                                },
                                contentDescription = null,
                                tint = colors.accentStrong,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when {
                                    isSwipingDownToMini -> if (swipeDragProgress >= 0.85f) "Release for mini player" else "Swipe down for mini player"
                                    playerState.isFullScreenLandscape -> if (swipeDragProgress >= 0.85f) "Release for portrait" else "Swipe down for portrait"
                                    else -> if (swipeDragProgress >= 0.85f) "Release for full screen" else "Swipe up for full screen"
                                },
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.5.sp,
                                color = Color.White
                            )
                        }
                    }
                }

            // Live Subtitle Cues Overlay
            if (playerState.activeSubtitleCues.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 18.dp, vertical = if (controlsVisible) 64.dp else 18.dp)
                        .testTag("video_subtitle_overlay")
                ) {
                    Text(
                        text = playerState.activeSubtitleCues.joinToString("\n"),
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Gesture HUD Indicator (Seek / Brightness / Volume)
            androidx.compose.animation.AnimatedVisibility(
                visible = gestureHudText != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.Black.copy(alpha = 0.72f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val hud = gestureHudText.orEmpty()
                        val icon = when {
                            hud.startsWith("Brightness") -> Icons.Rounded.Brightness6
                            hud.startsWith("Volume") -> Icons.Rounded.VolumeUp
                            else -> null
                        }
                        if (icon != null) {
                            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = hud,
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                }
            }

            // Buffering / Resolving Indicator
            if (playerState.isResolving || playerState.isBuffering) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = colors.accentStrong,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(36.dp)
                    )
                    if (playerState.isResolving) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Resolving video stream...",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                }
            }

            // Error Overlay with Retry
            if (!playerState.errorMessage.isNullOrBlank() && !playerState.isResolving) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.78f))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = playerState.errorMessage,
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 13.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentStrong,
                            contentColor = colors.onAccentStrong
                        )
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Retry Stream")
                    }
                }
            }

            // Interactive Video Controls Overlay (hidden in PiP)
            androidx.compose.animation.AnimatedVisibility(
                visible = controlsVisible && !playerState.isInPictureInPicture && playerState.errorMessage.isNullOrBlank(),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.65f),
                                    Color.Black.copy(alpha = 0.22f),
                                    Color.Black.copy(alpha = 0.78f)
                                )
                            )
                        )
                        .then(if (isFullScreen) Modifier.displayCutoutPadding() else Modifier)
                ) {
                    // Top Control Row
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (playerState.isFullScreenLandscape) {
                                    controlsVisible = true
                                    onToggleFullScreen(false)
                                } else {
                                    onMinimize()
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("video_minimize_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = if (playerState.isFullScreenLandscape) "Return to portrait" else "Minimize video player",
                                tint = Color.White
                            )
                        }

                        if (playerState.isFullScreenLandscape) {
                            Text(
                                text = video.title,
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        // Quick Aspect Ratio badge pill
                        val ratioPillLabel = if (playerState.aspectRatioMode == VideoAspectRatioMode.AUTO && playerState.hasKnownVideoDimensions) {
                            playerState.detectedAspectRatioLabel
                        } else {
                            playerState.aspectRatioMode.shortLabel
                        }
                        Surface(
                            onClick = { showAspectRatioSheet = true },
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White.copy(alpha = 0.18f),
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .testTag("video_aspect_ratio_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AspectRatio,
                                    contentDescription = "Aspect ratio selector",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = ratioPillLabel,
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                            }
                        }

                        // Quality badge pill (Auto / Resolution)
                        val qualityBadgeLabel = when {
                            playerState.isLocalOfflineVideo -> playerState.qualityStatusLabel.ifBlank { "Offline" }
                            playerState.isAutoQualityMode -> {
                                val res = playerState.selectedVideoStream?.resolutionLabel
                                if (!res.isNullOrBlank()) "Auto • $res" else "Auto"
                            }
                            else -> playerState.selectedVideoStream?.resolutionLabel ?: "Auto"
                        }
                        Surface(
                            onClick = { showSettingsSheet = true },
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White.copy(alpha = 0.18f),
                            modifier = Modifier.testTag("video_quality_chip")
                        ) {
                            Text(
                                text = qualityBadgeLabel,
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        // Picture-in-Picture button
                        IconButton(
                            onClick = { (activity as? MainActivity)?.enterVideoPictureInPicture() },
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("video_pip_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PictureInPictureAlt,
                                contentDescription = "Enter Picture-in-Picture",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Settings (Aspect Ratio / Quality / Speed / Subtitles / Audio)
                        IconButton(
                            onClick = { showSettingsSheet = true },
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("video_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "Video Aspect Ratio, Quality, Speed and Subtitles",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Center Transport Row (Prev, Rewind, Play/Pause, Forward, Next)
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onPlayPrevious,
                            enabled = true,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("video_prev_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious,
                                contentDescription = "Previous video",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                onSeekBy(-gestureSettings.doubleTapSeekMs)
                                gestureHudText = "-${gestureSettings.doubleTapSeekSeconds}s"
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("video_rewind_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Replay10,
                                contentDescription = "Rewind ${gestureSettings.doubleTapSeekSeconds} seconds",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Surface(
                            onClick = onTogglePlayPause,
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .size(58.dp)
                                .testTag("video_play_pause_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                onSeekBy(gestureSettings.doubleTapSeekMs)
                                gestureHudText = "+${gestureSettings.doubleTapSeekSeconds}s"
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("video_forward_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Forward10,
                                contentDescription = "Forward ${gestureSettings.doubleTapSeekSeconds} seconds",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        IconButton(
                            onClick = onPlayNext,
                            enabled = playerState.hasNextInQueue,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("video_next_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = "Next video",
                                tint = if (playerState.hasNextInQueue) Color.White else Color.White.copy(alpha = 0.35f),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    // Isolated Bottom Seekbar + Timestamps + Fullscreen Toggle
                    // Collects 500ms position ticks locally so playback never recomposes the player surface or feed
                    VideoControlsTimeline(
                        fallbackPositionMs = playerState.currentPositionMs,
                        fallbackDurationMs = playerState.durationMs,
                        playbackProgressFlow = playbackProgressFlow,
                        playbackSpeed = playerState.playbackSpeed,
                        isFullScreenLandscape = playerState.isFullScreenLandscape,
                        colors = colors,
                        onScrubbingChanged = { isScrubbing = it },
                        onSeekTo = onSeekTo,
                        onToggleFullScreen = { onToggleFullScreen(!playerState.isFullScreenLandscape) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // 2. Portrait Video Details, Account Actions, Description, Comments & Related Videos
        if (!isFullScreen) {
            val isWatchLater = video.videoId in watchLaterIds
            val isSaved = video.videoId in savedVideoIds
            val downloadEligibility = remember(video.videoId, details, playerState.isLocalOfflineVideo) {
                if (playerState.isLocalOfflineVideo) {
                    VideoDownloadEligibility.NotPermitted("Already downloaded")
                } else {
                    VideoDownloadEligibilityEvaluator.evaluate(video, details)
                }
            }
            val (moreFromChannel, relatedVideos) = remember(video, details, recommendationFeedback) {
                VideoRecommendationEngine.partitionPlayerRecommendations(
                    activeVideo = video,
                    rawRelated = details?.relatedVideos.orEmpty(),
                    signals = data.video.VideoRecommendationSignals(feedbackEntries = recommendationFeedback)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .navigationBarsPadding()
                    .testTag("video_details_scroll"),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Title, Views & Upload Date
                item(key = "video_title_block") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                            .clickable { showDescriptionExpanded = !showDescriptionExpanded }
                    ) {
                        Text(
                            text = video.title,
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            lineHeight = 23.sp,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        val meta = listOfNotNull(
                            video.formattedViewCount(),
                            details?.uploadDateText ?: video.uploadDateText
                        ).joinToString(" • ")
                        if (meta.isNotBlank()) {
                            Text(
                                text = meta,
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                }

                // Channel Info Row + SONORA Subscribe + SONORA Notification Bell
                item(key = "video_channel_row") {
                    val isSubscribed = playerState.isSubscribedLocally
                    val isAlertsAll = isSubscribed &&
                        playerState.notificationPreference == ChannelNotificationPreference.ALL
                    val subscribeContainerColor by animateColorAsState(
                        targetValue = if (isSubscribed) colors.surfaceTinted else colors.accentStrong,
                        label = "sonora_subscribe_bg"
                    )
                    val subscribeContentColor by animateColorAsState(
                        targetValue = if (isSubscribed) colors.textPrimary else colors.onAccentStrong,
                        label = "sonora_subscribe_fg"
                    )
                    val bellScale by animateFloatAsState(
                        targetValue = if (isAlertsAll) 1.08f else 1.0f,
                        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
                        label = "sonora_bell_scale"
                    )
                    val bellBgColor by animateColorAsState(
                        targetValue = if (isAlertsAll) colors.accentPill else colors.surfaceTinted,
                        label = "sonora_bell_bg"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    video.channelUrl?.let { onOpenChannel(it, video.channelName) }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ChannelAvatarBadge(
                                avatarUrl = channelModel.avatarUrl,
                                channelName = channelModel.name,
                                colors = colors,
                                sizeDp = 42
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = channelModel.name,
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                channelModel.formattedSubscribers()?.let { subs ->
                                    Text(
                                        text = subs,
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 11.5.sp,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleSubscribe(channelModel)
                            },
                            shape = RoundedCornerShape(22.dp),
                            color = subscribeContainerColor,
                            border = if (isSubscribed) BorderStroke(1.dp, colors.accentStrong.copy(alpha = 0.55f)) else null,
                            modifier = Modifier
                                .height(42.dp)
                                .testTag("sonora_subscribe_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                if (isSubscribed) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = colors.accentStrong,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = if (isSubscribed) "Subscribed" else "Subscribe",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    color = subscribeContentColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Surface(
                            onClick = {
                                if (isSubscribed) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showNotificationSheet = true
                                }
                            },
                            enabled = isSubscribed,
                            shape = CircleShape,
                            color = bellBgColor,
                            border = BorderStroke(
                                width = if (isAlertsAll) 1.25.dp else 0.75.dp,
                                color = if (isAlertsAll) colors.accentStrong else colors.cardBorder
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .graphicsLayer {
                                    scaleX = bellScale
                                    scaleY = bellScale
                                    alpha = if (isSubscribed) 1f else 0.38f
                                }
                                .testTag("sonora_notification_bell_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = when {
                                        !isSubscribed -> Icons.Rounded.NotificationsNone
                                        isAlertsAll -> Icons.Rounded.NotificationsActive
                                        else -> Icons.Rounded.NotificationsOff
                                    },
                                    contentDescription = when {
                                        !isSubscribed -> "Subscribe in ${domain.branding.AppBranding.APP_NAME} to enable notifications"
                                        isAlertsAll -> "${domain.branding.AppBranding.APP_NAME} channel notifications on"
                                        else -> "${domain.branding.AppBranding.APP_NAME} channel notifications off"
                                    },
                                    tint = if (isAlertsAll) colors.accentStrong else colors.textPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Custom SONORA Interaction Bar: SONORA Like | SONORA Dislike | Share | Save
                item(key = "video_action_chips") {
                    val isSavedAnywhere = isSaved || isWatchLater ||
                        videoPlaylists.any { pl -> pl.videos.any { it.videoId == video.videoId } }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.testTag("sonora_interaction_bar")
                    ) {
                        item(key = "act_like") {
                            val liked = playerState.ratingState == VideoRatingState.LIKED
                            VideoActionPill(
                                icon = if (liked) Icons.Rounded.ThumbUp else Icons.Rounded.ThumbUpOffAlt,
                                label = "Like",
                                active = liked,
                                colors = colors,
                                testTag = "sonora_like_button",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleLike()
                                }
                            )
                        }
                        item(key = "act_dislike") {
                            val disliked = playerState.ratingState == VideoRatingState.DISLIKED
                            VideoActionPill(
                                icon = if (disliked) Icons.Rounded.ThumbDown else Icons.Rounded.ThumbDownOffAlt,
                                label = "Dislike",
                                active = disliked,
                                colors = colors,
                                testTag = "sonora_dislike_button",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleDislike()
                                }
                            )
                        }
                        item(key = "act_share") {
                            VideoActionPill(
                                icon = Icons.Rounded.Share,
                                label = "Share",
                                active = false,
                                colors = colors,
                                testTag = "sonora_share_button",
                                onClick = { shareVideoLink(context, video) }
                            )
                        }
                        item(key = "act_save") {
                            VideoActionPill(
                                icon = if (isSavedAnywhere) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                label = if (isSavedAnywhere) "Saved" else "Save",
                                active = isSavedAnywhere,
                                colors = colors,
                                testTag = "sonora_save_button",
                                onClick = { showSaveSheet = true }
                            )
                        }
                        if (onRequestDownloadVideo != null && downloadEligibility is VideoDownloadEligibility.Permitted) {
                            item(key = "act_download") {
                                VideoActionPill(
                                    icon = Icons.Rounded.Download,
                                    label = "Download",
                                    active = false,
                                    colors = colors,
                                    testTag = "video_download_button",
                                    onClick = { onRequestDownloadVideo(video, details) }
                                )
                            }
                        }
                        if (playerState.queue.size > 1) {
                            item(key = "act_queue") {
                                VideoActionPill(
                                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                                    label = "Queue (${playerState.queue.size})",
                                    active = false,
                                    colors = colors,
                                    testTag = "video_queue_button",
                                    onClick = { showQueueSheet = true }
                                )
                            }
                        }
                    }
                }

                // Video Description Card (uses the clean space freed by removing Comments)
                val descriptionText = details?.description?.trim().orEmpty()
                if (descriptionText.isNotBlank()) {
                    item(key = "video_description_card") {
                        Surface(
                            onClick = { showDescriptionExpanded = !showDescriptionExpanded },
                            shape = RoundedCornerShape(18.dp),
                            color = colors.surfaceTinted,
                            border = BorderStroke(0.75.dp, colors.cardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp)
                                .testTag("video_description_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Description",
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = if (showDescriptionExpanded) "Show less" else "Show more",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.5.sp,
                                        color = colors.accentStrong
                                    )
                                }
                                Text(
                                    text = descriptionText,
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 12.5.sp,
                                    lineHeight = 18.sp,
                                    color = colors.textSecondary,
                                    maxLines = if (showDescriptionExpanded) Int.MAX_VALUE else 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Post-Video Recommendations Banner when playback finishes
                if (playerState.hasVideoEnded && relatedVideos.isNotEmpty()) {
                    item(key = "video_post_play_banner") {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = colors.accentPill.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, colors.accentStrong.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp)
                                .testTag("video_post_playback_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Finished Watching • Up Next",
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = relatedVideos.first().title,
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 12.sp,
                                        color = colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { onPlayRelatedVideo(relatedVideos.first(), relatedVideos) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.accentStrong,
                                        contentColor = colors.onAccentStrong
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("Play Next", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // More From This Channel
                if (moreFromChannel.isNotEmpty()) {
                    item(key = "video_more_from_channel_header") {
                        Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                            HorizontalDivider(color = colors.cardBorder)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "More From ${video.channelName}",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = colors.textPrimary
                            )
                        }
                    }
                    items(moreFromChannel, key = { "mfc_${it.videoId}" }) { rel ->
                        CompactVideoRowCard(
                            video = rel,
                            colors = colors,
                            isPlayingNow = rel.videoId == video.videoId,
                            onClick = { onPlayRelatedVideo(rel, moreFromChannel + relatedVideos) },
                            onChannelClick = rel.channelUrl?.takeIf { it.isNotBlank() }?.let { url ->
                                { onOpenChannel(url, rel.channelName) }
                            },
                            onMoreClick = { onVideoMoreClick(rel) },
                            modifier = Modifier.padding(horizontal = 18.dp)
                        )
                    }
                }

                // Related Videos / Up Next
                if (relatedVideos.isNotEmpty()) {
                    item(key = "video_related_header") {
                        Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                            HorizontalDivider(color = colors.cardBorder)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Related Videos",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = colors.textPrimary
                            )
                        }
                    }
                    items(relatedVideos, key = { "rel_${it.videoId}" }) { rel ->
                        CompactVideoRowCard(
                            video = rel,
                            colors = colors,
                            isPlayingNow = rel.videoId == video.videoId,
                            onClick = { onPlayRelatedVideo(rel, relatedVideos) },
                            onChannelClick = rel.channelUrl?.takeIf { it.isNotBlank() }?.let { url ->
                                { onOpenChannel(url, rel.channelName) }
                            },
                            onMoreClick = { onVideoMoreClick(rel) },
                            modifier = Modifier.padding(horizontal = 18.dp)
                        )
                    }
                }
            }
        }
    }
    }

    if (showSettingsSheet) {
        VideoSettingsSheet(
            videoStreams = details?.videoStreams.orEmpty(),
            selectedVideoStream = playerState.selectedVideoStream,
            isAutoQualityMode = playerState.isAutoQualityMode,
            qualityStatusLabel = playerState.qualityStatusLabel,
            actualResolutionDisplay = playerState.actualResolutionDisplay,
            actualFormatDisplay = playerState.actualFormatDisplay,
            audioStreams = details?.audioStreams.orEmpty(),
            selectedAudioStream = playerState.selectedAudioStream,
            subtitles = details?.subtitles.orEmpty(),
            selectedSubtitle = playerState.selectedSubtitle,
            playbackSpeed = playerState.playbackSpeed,
            aspectRatioMode = playerState.aspectRatioMode,
            detectedAspectRatioLabel = playerState.detectedAspectRatioLabel,
            hasKnownDimensions = playerState.hasKnownVideoDimensions,
            colors = colors,
            onSelectAutoQuality = onSelectAutoQuality,
            onSelectVideoStream = onSelectStream,
            onSelectAudioStream = onSelectAudio,
            onSelectSubtitle = onSelectSubtitle,
            onSelectSpeed = onSelectSpeed,
            onOpenAspectRatioSelector = { showAspectRatioSheet = true },
            onDismiss = { showSettingsSheet = false }
        )
    }

    if (showAspectRatioSheet) {
        VideoAspectRatioSheet(
            currentMode = playerState.aspectRatioMode,
            videoWidth = playerState.videoWidth,
            videoHeight = playerState.videoHeight,
            unappliedRotationDegrees = playerState.unappliedRotationDegrees,
            detectedRatioLabel = playerState.detectedAspectRatioLabel,
            hasKnownDimensions = playerState.hasKnownVideoDimensions,
            colors = colors,
            onSelectMode = { mode ->
                onSelectAspectRatio(mode)
                gestureHudText = if (mode == VideoAspectRatioMode.AUTO && playerState.hasKnownVideoDimensions) {
                    "Auto (${playerState.detectedAspectRatioLabel})"
                } else {
                    mode.displayTitle
                }
            },
            onDismiss = { showAspectRatioSheet = false }
        )
    }

    if (showQueueSheet) {
        VideoQueueSheet(
            queue = playerState.queue,
            activeVideoId = video.videoId,
            colors = colors,
            onPlayVideo = { target -> onPlayRelatedVideo(target, playerState.queue) },
            onRemoveFromQueue = onRemoveFromQueue,
            onMoreClick = onVideoMoreClick,
            onDismiss = { showQueueSheet = false }
        )
    }

    if (showSaveSheet) {
        SonoraSaveVideoSheet(
            video = video,
            isSaved = video.videoId in savedVideoIds,
            isWatchLater = video.videoId in watchLaterIds,
            playlists = videoPlaylists,
            colors = colors,
            onToggleSave = { onToggleSaveVideo(video) },
            onToggleWatchLater = { onToggleWatchLater(video) },
            onSelectPlaylist = { pl -> onAddVideoToPlaylist(pl.id, pl.name, video) },
            onOpenCreatePlaylist = { showPlaylistDialog = true },
            onDismiss = { showSaveSheet = false }
        )
    }

    if (showPlaylistDialog) {
        VideoPlaylistPickerDialog(
            video = video,
            playlists = videoPlaylists,
            colors = colors,
            onSelectPlaylist = { pl -> onAddVideoToPlaylist(pl.id, pl.name, video) },
            onCreatePlaylistWithVideo = { name -> onCreateVideoPlaylist(name, video) },
            onDismiss = { showPlaylistDialog = false }
        )
    }

    if (showNotificationSheet && playerState.isSubscribedLocally) {
        NotificationBellOptionsSheet(
            channel = channelModel,
            currentPreference = playerState.notificationPreference,
            supportsApi = true,
            colors = colors,
            onSelectPreference = { pref -> onSelectNotificationPref(channelModel, pref) },
            onDismiss = { showNotificationSheet = false }
        )
    }
}

@Composable
private fun VideoActionPill(
    icon: ImageVector,
    label: String,
    active: Boolean,
    colors: EditorialColors,
    testTag: String,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (active) colors.accentPill else colors.surfaceTinted,
        label = "action_pill_bg_$testTag"
    )
    val borderColor by animateColorAsState(
        targetValue = if (active) colors.accentStrong else colors.cardBorder,
        label = "action_pill_border_$testTag"
    )
    val iconTint by animateColorAsState(
        targetValue = if (active) colors.accentStrong else colors.textPrimary,
        label = "action_pill_tint_$testTag"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (active) 1.14f else 1.0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "action_pill_scale_$testTag"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = bgColor,
        border = BorderStroke(if (active) 1.15.dp else 0.75.dp, borderColor),
        modifier = Modifier
            .height(44.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontFamily = PlusJakartaFontFamily,
                fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 12.5.sp,
                color = if (active) colors.onAccentPill else colors.textPrimary
            )
        }
    }
}

/**
 * Isolated video surface viewport preferring hardware-composited [SurfaceView]
 * (avoiding TextureView GPU copy overhead and redundant update re-attachments)
 * while preserving aspect ratio, pinch-to-zoom, and two-finger pan.
 */
@Composable
private fun VideoSurfaceViewport(
    frameWidth: Dp,
    frameHeight: Dp,
    surfaceWidth: Dp,
    surfaceHeight: Dp,
    zoomScale: Float,
    offsetX: Float,
    offsetY: Float,
    onAttachSurfaceView: ((SurfaceView) -> Unit)?,
    onDetachSurfaceView: ((SurfaceView) -> Unit)?,
    onAttachTextureView: (TextureView) -> Unit,
    onDetachTextureView: (TextureView) -> Unit
) {
    val safeScale = zoomScale.coerceIn(0.85f, 3.0f)
    if (onAttachSurfaceView != null) {
        Box(
            modifier = Modifier
                .size(width = frameWidth, height = frameHeight)
                .clipToBounds()
                .testTag("video_aspect_frame"),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    SurfaceView(ctx).also { sv -> onAttachSurfaceView(sv) }
                },
                onRelease = { sv -> onDetachSurfaceView?.invoke(sv) },
                modifier = Modifier
                    .requiredSize(
                        width = surfaceWidth * safeScale,
                        height = surfaceHeight * safeScale
                    )
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .testTag("video_surface_view")
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(width = frameWidth, height = frameHeight)
                .graphicsLayer {
                    translationX = offsetX
                    translationY = offsetY
                    scaleX = safeScale
                    scaleY = safeScale
                }
                .clipToBounds()
                .testTag("video_aspect_frame"),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).also { tv -> onAttachTextureView(tv) }
                },
                onRelease = { tv -> onDetachTextureView(tv) },
                modifier = Modifier
                    .size(width = surfaceWidth, height = surfaceHeight)
                    .testTag("video_texture_view")
            )
        }
    }
}

/**
 * Isolated seekbar and timestamp bar collecting 500ms playback progress ticks locally
 * so continuous playback never triggers whole-screen or surface recompositions.
 */
@Composable
private fun VideoControlsTimeline(
    fallbackPositionMs: Long,
    fallbackDurationMs: Long,
    playbackProgressFlow: StateFlow<VideoPlaybackProgress>?,
    playbackSpeed: Float,
    isFullScreenLandscape: Boolean,
    colors: EditorialColors,
    onScrubbingChanged: (Boolean) -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleFullScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val liveProgress = playbackProgressFlow?.collectAsStateWithLifecycle()?.value
    val currentPosMs = liveProgress?.currentPositionMs ?: fallbackPositionMs
    val durationMs = (liveProgress?.durationMs?.takeIf { it > 0L }) ?: fallbackDurationMs

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableFloatStateOf(0f) }

    Column(modifier = modifier) {
        val maxDur = durationMs.coerceAtLeast(1L)
        val sliderVal = if (isScrubbing) {
            scrubPositionMs.coerceIn(0f, maxDur.toFloat())
        } else {
            currentPosMs.toFloat().coerceIn(0f, maxDur.toFloat())
        }
        Slider(
            value = sliderVal,
            onValueChange = {
                if (!isScrubbing) {
                    isScrubbing = true
                    onScrubbingChanged(true)
                }
                scrubPositionMs = it
            },
            onValueChangeFinished = {
                isScrubbing = false
                onScrubbingChanged(false)
                onSeekTo(scrubPositionMs.toLong())
            },
            valueRange = 0f..maxDur.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = colors.accentStrong,
                activeTrackColor = colors.accentStrong,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .testTag("video_seek_bar")
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${formatVideoTime(sliderVal.toLong())} / ${formatVideoTime(durationMs)}",
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.5.sp,
                color = Color.White,
                modifier = Modifier.testTag("video_time_display")
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (playbackSpeed != 1.0f) {
                    Text(
                        text = "${playbackSpeed}x",
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = colors.accentStrong,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
                IconButton(
                    onClick = onToggleFullScreen,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("video_fullscreen_toggle")
                ) {
                    Icon(
                        imageVector = if (isFullScreenLandscape) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                        contentDescription = if (isFullScreenLandscape) "Exit full screen" else "Full screen landscape",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

