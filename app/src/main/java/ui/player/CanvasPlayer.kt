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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PlaybackInfo
import com.example.model.PlayerAppearanceConfig
import com.example.model.PlayerGestureConfig
import com.example.model.Song
import com.example.ui.components.ArtworkAlbumView
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import ui.components.PlaybackControls
import ui.player.seekbar.SeekbarRenderer
import ui.player.seekbar.SeekbarStyle
import ui.theme.LocalEditorialColors

/**
 * Style 4: Canvas
 * Full-screen album artwork filling the entire player background with cinematic gradient scrim,
 * unified multi-gesture support, and a theme-aware floating control card.
 */
@Composable
fun CanvasPlayer(
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
    appearance: PlayerAppearanceConfig = PlayerAppearanceConfig.DEFAULT,
    gestureConfig: PlayerGestureConfig = PlayerGestureConfig.DEFAULT,
    gestureController: PlayerGestureController = rememberPlayerGestureController(),
    liveWaveform: FloatArray? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onCyclePlayerStyle: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val duration = (playbackInfo.durationMs).coerceAtLeast(song?.durationMs ?: 0L)
    val scale = appearance.typographyScale.coerceIn(0.85f, 1.25f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .playerArtworkGestures(
                config = gestureConfig,
                controller = gestureController,
                currentPositionMs = playbackInfo.currentPositionMs,
                durationMs = duration,
                isPlaying = playbackInfo.isPlaying,
                onTogglePlayPause = onTogglePlayPause,
                onPlayNext = onPlayNext,
                onPlayPrevious = onPlayPrevious,
                onSeekTo = onSeekTo,
                onOpenQueue = onOpenQueue,
                onClose = onClose,
                onArtworkLongPress = onArtworkLongPress,
                onToggleFavorite = onToggleFavorite,
                onCyclePlayerStyle = onCyclePlayerStyle
            )
    ) {
        ArtworkAlbumView(
            song = song,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = gestureController.artworkZoomScale
                    scaleY = gestureController.artworkZoomScale
                },
            cornerRadius = 0.dp,
            isPlaying = playbackInfo.isPlaying
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.65f),
                            Color.Black.copy(alpha = 0.15f),
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.90f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("collapse_now_playing_button")
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse", tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Black.copy(alpha = 0.48f),
                    border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.22f))
                ) {
                    Text(
                        text = "CANVAS IMMERSIVE",
                        fontFamily = OutfitFontFamily,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                IconButton(
                    onClick = onOpenQueue,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("open_queue_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue", tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(22.dp))
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            AnimatedVisibility(visible = gestureController.controlsVisible) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp)),
                    color = colors.surface.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(28.dp),
                    tonalElevation = 8.dp,
                    border = BorderStroke(1.dp, colors.cardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
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
                                    fontSize = (20f * scale).sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (appearance.showAlbumMetadata && !song?.album.isNullOrBlank()) {
                                        "${song?.artist ?: "Unknown Artist"} • ${song?.album}"
                                    } else {
                                        song?.artist ?: "Unknown Artist"
                                    },
                                    fontFamily = PlusJakartaFontFamily,
                                    color = colors.accentStrong,
                                    fontSize = (14f * scale).sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (appearance.showLyricsShortcut) {
                                IconButton(onClick = onOpenLyrics, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Default.Lyrics, contentDescription = "Lyrics", tint = colors.accentStrong, modifier = Modifier.size(22.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        SeekbarRenderer(
                            currentPositionMs = playbackInfo.currentPositionMs,
                            durationMs = duration,
                            onSeekTo = onSeekTo,
                            isPlaying = playbackInfo.isPlaying,
                            style = seekbarStyle,
                            liveWaveform = liveWaveform,
                            thicknessScale = appearance.seekbarThicknessScale,
                            hapticFeedbackEnabled = gestureConfig.hapticFeedbackEnabled,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        PlaybackControls(
                            playbackInfo = playbackInfo,
                            onTogglePlayPause = onTogglePlayPause,
                            onPlayNext = onPlayNext,
                            onPlayPrevious = onPlayPrevious,
                            onToggleShuffle = onToggleShuffle,
                            onCycleRepeat = onCycleRepeat,
                            appearance = appearance,
                            accentColor = colors.accentStrong,
                            onAccentColor = colors.onAccentStrong,
                            primaryButtonSize = 64.dp
                        )
                    }
                }
            }
        }
    }
}
