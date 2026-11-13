package ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PlaybackInfo
import com.example.model.PlayerAppearanceConfig
import com.example.model.PlayerGestureConfig
import com.example.model.Song
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import ui.components.ArtworkContainer
import ui.player.seekbar.SeekbarRenderer
import ui.player.seekbar.SeekbarStyle
import ui.theme.LocalEditorialColors

/**
 * Style 2: Gesture
 * Large album artwork occupying most of the screen with customizable swipe,
 * double-tap seek, pinch-to-zoom, swipe-up queue, and theme-matched floating dock.
 */
@Composable
fun GesturePlayer(
    song: Song?,
    playbackInfo: PlaybackInfo,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onClose: () -> Unit,
    onArtworkLongPress: () -> Unit,
    seekbarStyle: SeekbarStyle = SeekbarStyle.DEFAULT,
    gesturesEnabled: Boolean = true,
    swipeArtworkToSkip: Boolean = true,
    doubleTapToSeek: Boolean = true,
    swipeDownToCollapse: Boolean = true,
    appearance: PlayerAppearanceConfig = PlayerAppearanceConfig.DEFAULT,
    gestureConfig: PlayerGestureConfig = PlayerGestureConfig.DEFAULT,
    gestureController: PlayerGestureController = rememberPlayerGestureController(),
    liveWaveform: FloatArray? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onCyclePlayerStyle: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val effectiveGestureConfig = gestureConfig.copy(
        gesturesEnabled = gesturesEnabled && gestureConfig.gesturesEnabled,
        swipeArtworkEnabled = swipeArtworkToSkip && gestureConfig.swipeArtworkEnabled,
        doubleTapEnabled = doubleTapToSeek && gestureConfig.doubleTapEnabled,
        swipeDownToMinimizeEnabled = swipeDownToCollapse && gestureConfig.swipeDownToMinimizeEnabled
    )

    val currentPos = playbackInfo.currentPositionMs
    val duration = (playbackInfo.durationMs).coerceAtLeast(song?.durationMs ?: 0L)

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("collapse_now_playing_button")
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse", tint = colors.textPrimary, modifier = Modifier.size(28.dp))
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.accentPill,
                    border = BorderStroke(1.dp, colors.cardBorder)
                ) {
                    Text(
                        text = "GESTURE MODE",
                        fontFamily = OutfitFontFamily,
                        color = colors.onAccentPill,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
                IconButton(
                    onClick = onOpenQueue,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("open_queue_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue", tint = colors.textSecondary, modifier = Modifier.size(22.dp))
                }
            }

            ArtworkContainer(
                song = song,
                playbackInfo = playbackInfo,
                appearance = appearance,
                gestureConfig = effectiveGestureConfig,
                gestureController = gestureController,
                onTogglePlayPause = onTogglePlayPause,
                onPlayNext = onPlayNext,
                onPlayPrevious = onPlayPrevious,
                onSeekTo = onSeekTo,
                onOpenQueue = onOpenQueue,
                onClose = onClose,
                onArtworkLongPress = onArtworkLongPress,
                onToggleFavorite = onToggleFavorite,
                onCyclePlayerStyle = onCyclePlayerStyle,
                defaultCornerRadius = 32.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Swipe Previous Hint", tint = Color.White.copy(alpha = 0.78f), modifier = Modifier.size(22.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Swipe Next Hint", tint = Color.White.copy(alpha = 0.78f), modifier = Modifier.size(22.dp))
                    }
                }
            }

            AnimatedVisibility(visible = gestureController.controlsVisible) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    color = colors.surface,
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 4.dp,
                    border = BorderStroke(1.dp, colors.cardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        SeekbarRenderer(
                            currentPositionMs = currentPos,
                            durationMs = duration,
                            onSeekTo = onSeekTo,
                            isPlaying = playbackInfo.isPlaying,
                            style = seekbarStyle,
                            showTimestamps = false,
                            liveWaveform = liveWaveform,
                            thicknessScale = appearance.seekbarThicknessScale,
                            hapticFeedbackEnabled = effectiveGestureConfig.hapticFeedbackEnabled
                        )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song?.title ?: "No Song Selected",
                                fontFamily = OutfitFontFamily,
                                color = colors.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${song?.artist ?: "Unknown"} • ${formatDurationMs(currentPos)} / ${formatDurationMs(duration)}",
                                fontFamily = PlusJakartaFontFamily,
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onOpenLyrics, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Lyrics, contentDescription = "Lyrics", tint = colors.accentStrong, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                onClick = onTogglePlayPause,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("play_pause_button"),
                                shape = CircleShape,
                                color = colors.accentStrong
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (playbackInfo.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (playbackInfo.isPlaying) "Pause" else "Play",
                                        tint = colors.onAccentStrong,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
}
