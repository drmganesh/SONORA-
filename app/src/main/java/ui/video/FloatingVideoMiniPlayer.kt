package ui.video

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import domain.video.VideoPlaybackProgress
import domain.video.VideoPlayerUiState
import kotlinx.coroutines.flow.StateFlow
import ui.theme.EditorialColors

/**
 * Floating SONORA Video Mini-Player pinned cleanly above the bottom navigation bar
 * when an Online Video is active and minimized.
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
    modifier: Modifier = Modifier
) {
    val video = playerState.activeVideo ?: return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = colors.surfaceElevated,
            border = BorderStroke(1.dp, colors.accentStrong.copy(alpha = 0.45f)),
            shadowElevation = 10.dp,
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .clickable(onClick = onExpand)
                .testTag("floating_video_mini_player")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 6.dp, top = 7.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VideoThumbnailBox(
                        video = video,
                        colors = colors,
                        compactBadge = true,
                        modifier = Modifier
                            .width(84.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp))
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = video.title,
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (playerState.isResolving) {
                                "Resolving video stream..."
                            } else {
                                video.channelName
                            },
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 11.5.sp,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (playerState.isResolving || playerState.isBuffering) {
                        Box(
                            modifier = Modifier.size(44.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = colors.accentStrong,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onTogglePlayPause,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("video_mini_play_pause")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(colors.accentStrong),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (playerState.isPlaying) "Pause video" else "Play video",
                                    tint = colors.onAccentStrong,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    if (playerState.hasNextInQueue) {
                        IconButton(
                            onClick = onPlayNext,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("video_mini_next")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = "Next video",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("video_mini_close")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close video player",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Isolated live progress bar (collects 500ms ticks without recomposing the mini-player row)
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
            .background(colors.cardBorder.copy(alpha = 0.4f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(2.5.dp)
                .background(colors.accentStrong)
        )
    }
}
