package ui.player.styles

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import ui.components.AdaptiveSeekbar
import ui.components.ArtworkContainer
import ui.components.PlaybackControls
import ui.player.PlayerGestureController
import ui.player.PlayerRenderState
import ui.player.PlayerStyleCallbacks
import ui.player.PlayerStyleManager
import ui.theme.LocalEditorialColors

/**
 * Style 2: Minimal
 * Whisper-quiet editorial presentation with compact centered artwork,
 * generous open space, essential metadata, and distraction-free transport.
 */
@Composable
fun MinimalPlayer(
    state: PlayerRenderState,
    callbacks: PlayerStyleCallbacks,
    gestureController: PlayerGestureController,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val song = state.song
    val duration = state.playbackInfo.durationMs.coerceAtLeast(song?.durationMs ?: 0L)
    val accent = PlayerStyleManager.resolveAccentColor(state.appearance, colors, state.dynamicPalette)
    val scale = state.appearance.typographyScale.coerceIn(0.85f, 1.25f)
    val artworkFraction = (state.appearance.artworkSize.fraction * 0.56f).coerceIn(0.38f, 0.68f)

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
                .widthIn(max = 560.dp)
                .padding(horizontal = 28.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = callbacks.onClose,
                    modifier = Modifier.size(48.dp).testTag("collapse_now_playing_button")
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse", tint = colors.textPrimary)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.appearance.showLyricsShortcut) {
                        IconButton(
                            onClick = callbacks.onOpenLyrics,
                            modifier = Modifier.size(48.dp).testTag("minimal_lyrics_button")
                        ) {
                            Icon(Icons.Default.Lyrics, contentDescription = "Lyrics", tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                        }
                    }
                    if (state.appearance.showQueueShortcut) {
                        IconButton(
                            onClick = callbacks.onOpenQueue,
                            modifier = Modifier.size(48.dp).testTag("open_queue_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue", tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                        }
                    }
                    IconButton(
                        onClick = callbacks.onOpenCustomization,
                        modifier = Modifier.size(48.dp).testTag("customize_player_button")
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = "Customize Player", tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.5f))

            ArtworkContainer(
                song = song,
                playbackInfo = state.playbackInfo,
                appearance = state.appearance,
                gestureConfig = state.gestureConfig,
                gestureController = gestureController,
                onTogglePlayPause = callbacks.onTogglePlayPause,
                onPlayNext = callbacks.onPlayNext,
                onPlayPrevious = callbacks.onPlayPrevious,
                onSeekTo = callbacks.onSeekTo,
                onOpenQueue = callbacks.onOpenQueue,
                onClose = callbacks.onClose,
                onArtworkLongPress = callbacks.onArtworkLongPress,
                onToggleFavorite = callbacks.onToggleFavorite,
                onCyclePlayerStyle = callbacks.onCyclePlayerStyle,
                defaultCornerRadius = 24.dp,
                modifier = Modifier
                    .fillMaxWidth(artworkFraction)
                    .aspectRatio(1f)
            )

            Spacer(modifier = Modifier.weight(0.4f))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = song?.title ?: "No Song Selected",
                    fontFamily = OutfitFontFamily,
                    color = colors.textPrimary,
                    fontSize = (21f * scale).sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (state.appearance.showAlbumMetadata && !song?.album.isNullOrBlank()) {
                        "${song?.artist ?: "Unknown Artist"} — ${song?.album}"
                    } else {
                        song?.artist ?: "Unknown Artist"
                    },
                    fontFamily = PlusJakartaFontFamily,
                    color = colors.textSecondary,
                    fontSize = (13f * scale).sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (state.appearance.showAudioFormatMetadata && song != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = PlayerStyleManager.formatTrackQualityBadge(song),
                        fontFamily = PlusJakartaFontFamily,
                        color = accent.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.8.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            AnimatedVisibility(visible = gestureController.controlsVisible) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    AdaptiveSeekbar(
                        currentPositionMs = state.playbackInfo.currentPositionMs,
                        durationMs = duration,
                        onSeekTo = callbacks.onSeekTo,
                        isPlaying = state.playbackInfo.isPlaying,
                        style = state.seekbarStyle,
                        appearance = state.appearance,
                        liveWaveform = state.waveformSamples,
                        hapticFeedbackEnabled = state.gestureConfig.hapticFeedbackEnabled,
                        accentColor = accent
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    PlaybackControls(
                        playbackInfo = state.playbackInfo,
                        onTogglePlayPause = callbacks.onTogglePlayPause,
                        onPlayNext = callbacks.onPlayNext,
                        onPlayPrevious = callbacks.onPlayPrevious,
                        onToggleShuffle = callbacks.onToggleShuffle,
                        onCycleRepeat = callbacks.onCycleRepeat,
                        appearance = state.appearance,
                        accentColor = accent,
                        primaryButtonSize = 62.dp
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.3f))
        }
    }
}
