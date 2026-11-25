package ui.video

import android.view.SurfaceView
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import domain.video.VideoAspectRatioCalculator
import domain.video.VideoAspectRatioMode
import domain.video.VideoPlayerUiState
import ui.theme.EditorialColors

/**
 * Dedicated edge-to-edge video-only surface rendered exclusively while the Activity is in
 * Android System Picture-in-Picture mode.
 *
 * Omits Home screen, bottom navigation, music mini-player, search, settings, recommendations,
 * system-bar padding, and all overlays so the Android PiP window displays ONLY the active
 * Media3 video surface at its true aspect ratio using the existing single ExoPlayer instance.
 */
@Composable
fun SystemPipVideoScreen(
    playerState: VideoPlayerUiState,
    colors: EditorialColors,
    onAttachSurfaceView: ((SurfaceView) -> Unit)? = null,
    onDetachSurfaceView: ((SurfaceView) -> Unit)? = null,
    onAttachTextureView: ((TextureView) -> Unit)? = null,
    onDetachTextureView: ((TextureView) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val video = playerState.activeVideo ?: return
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("system_pip_video_only_screen")
            .semantics {
                contentDescription = "Picture-in-picture video: ${video.title}"
            },
        contentAlignment = Alignment.Center
    ) {
        val vpWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val vpHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        val surfaceLayout = remember(
            vpWidthPx,
            vpHeightPx,
            playerState.videoWidth,
            playerState.videoHeight,
            playerState.pixelWidthHeightRatio,
            playerState.unappliedRotationDegrees
        ) {
            VideoAspectRatioCalculator.computeSurfaceLayout(
                viewportWidthPx = vpWidthPx,
                viewportHeightPx = vpHeightPx,
                videoWidth = playerState.videoWidth,
                videoHeight = playerState.videoHeight,
                pixelWidthHeightRatio = playerState.pixelWidthHeightRatio,
                unappliedRotationDegrees = playerState.unappliedRotationDegrees,
                mode = VideoAspectRatioMode.FIT
            )
        }

        val surfaceWidthDp = with(density) { surfaceLayout.surfaceWidthPx.toDp() }
        val surfaceHeightDp = with(density) { surfaceLayout.surfaceHeightPx.toDp() }

        // Only show thumbnail fallback while stream is still resolving before video frames arrive
        if (playerState.isResolving && !playerState.hasKnownVideoDimensions) {
            VideoThumbnailBox(
                video = video,
                colors = colors,
                compactBadge = true,
                modifier = Modifier.size(width = surfaceWidthDp, height = surfaceHeightDp)
            )
        }

        if (onAttachSurfaceView != null) {
            SystemPipSurfaceViewHolder(
                onAttachSurfaceView = onAttachSurfaceView,
                onDetachSurfaceView = onDetachSurfaceView,
                modifier = Modifier
                    .size(width = surfaceWidthDp, height = surfaceHeightDp)
                    .testTag("system_pip_video_surface")
            )
        } else if (onAttachTextureView != null) {
            SystemPipTextureViewHolder(
                onAttachTextureView = onAttachTextureView,
                onDetachTextureView = onDetachTextureView,
                modifier = Modifier
                    .size(width = surfaceWidthDp, height = surfaceHeightDp)
                    .testTag("system_pip_video_surface")
            )
        }
    }
}

@Composable
private fun SystemPipSurfaceViewHolder(
    onAttachSurfaceView: (SurfaceView) -> Unit,
    onDetachSurfaceView: ((SurfaceView) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val latestAttach by rememberUpdatedState(onAttachSurfaceView)
    val latestDetach by rememberUpdatedState(onDetachSurfaceView)

    AndroidView(
        factory = { ctx ->
            SurfaceView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                latestAttach(this)
            }
        },
        update = { view ->
            // Idempotent in VideoPlaybackController — avoids re-attach churn while guaranteeing attachment
            latestAttach(view)
        },
        onRelease = { view ->
            latestDetach?.invoke(view)
        },
        modifier = modifier
    )
}

@Composable
private fun SystemPipTextureViewHolder(
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
                latestAttach(this)
            }
        },
        update = { view ->
            latestAttach(view)
        },
        onRelease = { view ->
            latestDetach?.invoke(view)
        },
        modifier = modifier
    )
}
