package ui.player

import androidx.compose.foundation.BorderStroke
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
import ui.components.PlaybackControls
import ui.player.seekbar.SeekbarRenderer
import ui.player.seekbar.SeekbarStyle
import ui.theme.LocalEditorialColors

/**
 * Style 3: Editorial
 * Music-magazine inspired two-tone layout anchored to LocalEditorialColors.
 */
@Composable
fun EditorialPlayer(
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
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.1f)
                    .padding(horizontal = 20.dp, vertical = 8.dp)
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "SONORA GAZETTE • ISSUE 04",
                            fontFamily = OutfitFontFamily,
                            color = colors.accentStrong,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        if (appearance.showAudioFormatMetadata) {
                            Text(
                                text = song?.audioFormatInfo ?: "THE SOUNDTRACK COLLECTION",
                                fontFamily = PlusJakartaFontFamily,
                                color = colors.textMuted,
                                fontSize = 10.sp,
                                letterSpacing = 0.8.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
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

                ArtworkContainer(
                    song = song,
                    playbackInfo = playbackInfo,
                    appearance = appearance,
                    gestureConfig = gestureConfig,
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
                    defaultCornerRadius = 22.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = colors.surface.copy(alpha = 0.9f),
                        border = BorderStroke(1.dp, colors.cardBorder)
                    ) {
                        Text(
                            text = "PLATE N° 01 • HIGH FIDELITY",
                            fontFamily = OutfitFontFamily,
                            color = colors.accentStrong,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.9f)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(32.dp)),
                color = colors.surface,
                shape = RoundedCornerShape(32.dp),
                tonalElevation = 4.dp,
                border = BorderStroke(1.dp, colors.cardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 22.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "FEATURED SELECTION",
                                fontFamily = OutfitFontFamily,
                                color = colors.accentStrong,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )
                            if (appearance.showLyricsShortcut) {
                                IconButton(onClick = onOpenLyrics, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Default.Lyrics, contentDescription = "Lyrics", tint = colors.accentStrong, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                        Text(
                            text = song?.title ?: "No Song Selected",
                            fontFamily = OutfitFontFamily,
                            color = colors.textPrimary,
                            fontSize = (24f * scale).sp,
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
                            fontSize = (15f * scale).sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
                        primaryButtonSize = 64.dp
                    )
                }
            }
        }
    }
}
