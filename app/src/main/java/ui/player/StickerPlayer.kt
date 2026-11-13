package ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
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
 * Style 6: Sticker
 * Die-cut sticker artwork framing adapted cleanly to LocalEditorialColors.
 */
@Composable
fun StickerPlayer(
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
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
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
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse", tint = colors.textPrimary, modifier = Modifier.size(28.dp))
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.accentPill,
                    border = BorderStroke(1.dp, colors.cardBorder)
                ) {
                    Text(
                        text = "STICKER DIE-CUT",
                        fontFamily = OutfitFontFamily,
                        color = colors.onAccentPill,
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
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue", tint = colors.textSecondary, modifier = Modifier.size(22.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth(appearance.artworkSize.fraction)
                    .aspectRatio(1f)
                    .graphicsLayer {
                        scaleX = gestureController.artworkZoomScale
                        scaleY = gestureController.artworkZoomScale
                    }
                    .rotate(-2.5f)
                    .shadow(16.dp, RoundedCornerShape(32.dp), ambientColor = colors.shadowColor, spotColor = colors.shadowColor)
                    .clip(RoundedCornerShape(32.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.cardBorder, RoundedCornerShape(32.dp))
                    .padding(6.dp)
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
                    .testTag("player_artwork_box"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(27.dp))
                ) {
                    ArtworkAlbumView(
                        song = song,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 27.dp,
                        isPlaying = playbackInfo.isPlaying
                    )

                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(14.dp)
                            .rotate(4f),
                        shape = RoundedCornerShape(10.dp),
                        color = colors.accentStrong,
                        shadowElevation = 6.dp
                    ) {
                        Text(
                            text = "★ SONORA SOUND",
                            fontFamily = OutfitFontFamily,
                            color = colors.onAccentStrong,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp)),
                color = colors.surface,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, colors.cardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song?.title ?: "No Song Selected",
                            fontFamily = OutfitFontFamily,
                            color = colors.textPrimary,
                            fontSize = (18f * scale).sp,
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
                            color = colors.textSecondary,
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
            }

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
                primaryButtonSize = 66.dp
            )
        }
    }
}
