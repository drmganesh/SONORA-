package ui.player.styles

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
 * Style 18: Neon
 * Electric dark AMOLED presentation with luminous glowing accent aura around
 * artwork, neon progress bar, and crisp high-contrast metadata.
 */
@Composable
fun NeonPlayer(
    state: PlayerRenderState,
    callbacks: PlayerStyleCallbacks,
    gestureController: PlayerGestureController,
    modifier: Modifier = Modifier
) {
    val palette = state.dynamicPalette
    val neonGlow = if (palette.primary.luminance() < 0.25f) Color(0xFF4AF2FF) else palette.primary
    val song = state.song
    val duration = state.playbackInfo.durationMs.coerceAtLeast(song?.durationMs ?: 0L)
    val scale = state.appearance.typographyScale.coerceIn(0.85f, 1.25f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        neonGlow.copy(alpha = 0.20f),
                        Color(0xFF05060B),
                        Color(0xFF000000)
                    )
                )
            )
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = callbacks.onClose,
                    modifier = Modifier.size(48.dp).testTag("collapse_now_playing_button")
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse", tint = Color.White)
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = neonGlow.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, neonGlow.copy(alpha = 0.7f))
                ) {
                    Text(
                        text = if (state.appearance.showAudioFormatMetadata && song != null) {
                            PlayerStyleManager.formatTrackQualityBadge(song)
                        } else {
                            "NEON STUDIO"
                        },
                        fontFamily = OutfitFontFamily,
                        color = neonGlow,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }

                Row {
                    IconButton(
                        onClick = callbacks.onOpenQueue,
                        modifier = Modifier.size(48.dp).testTag("open_queue_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue", tint = Color.White)
                    }
                    IconButton(
                        onClick = callbacks.onOpenCustomization,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = "Customize Player", tint = neonGlow)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth(state.appearance.artworkSize.fraction.coerceIn(0.72f, 0.92f))
                    .aspectRatio(1f)
                    .shadow(28.dp, RoundedCornerShape(30.dp), ambientColor = neonGlow, spotColor = neonGlow)
                    .border(2.dp, neonGlow.copy(alpha = 0.85f), RoundedCornerShape(30.dp))
                    .padding(4.dp)
            ) {
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
                    defaultCornerRadius = 26.dp,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song?.title ?: "No Track Selected",
                            fontFamily = OutfitFontFamily,
                            color = Color.White,
                            fontSize = (23f * scale).sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (state.appearance.showAlbumMetadata && !song?.album.isNullOrBlank()) {
                                "${song?.artist ?: "Unknown"} • ${song?.album}"
                            } else {
                                song?.artist ?: "Unknown Artist"
                            },
                            fontFamily = PlusJakartaFontFamily,
                            color = Color(0xFFB4BCD0),
                            fontSize = (13f * scale).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row {
                        IconButton(onClick = callbacks.onToggleFavorite, modifier = Modifier.size(48.dp)) {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = "Favorite",
                                tint = if (song?.isFavorite == true) neonGlow else Color(0xFF7B8499)
                            )
                        }
                        if (state.appearance.showLyricsShortcut) {
                            IconButton(onClick = callbacks.onOpenLyrics, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Lyrics, contentDescription = "Lyrics", tint = neonGlow)
                            }
                        }
                    }
                }

                AdaptiveSeekbar(
                    currentPositionMs = state.playbackInfo.currentPositionMs,
                    durationMs = duration,
                    onSeekTo = callbacks.onSeekTo,
                    isPlaying = state.playbackInfo.isPlaying,
                    style = state.seekbarStyle,
                    appearance = state.appearance,
                    liveWaveform = state.waveformSamples,
                    hapticFeedbackEnabled = state.gestureConfig.hapticFeedbackEnabled,
                    accentColor = neonGlow,
                    inactiveColor = Color(0xFF252A3B)
                )

                PlaybackControls(
                    playbackInfo = state.playbackInfo,
                    onTogglePlayPause = callbacks.onTogglePlayPause,
                    onPlayNext = callbacks.onPlayNext,
                    onPlayPrevious = callbacks.onPlayPrevious,
                    onToggleShuffle = callbacks.onToggleShuffle,
                    onCycleRepeat = callbacks.onCycleRepeat,
                    appearance = state.appearance,
                    accentColor = neonGlow,
                    onAccentColor = Color(0xFF06080F),
                    iconTint = Color.White,
                    mutedTint = Color(0xFF7B8499),
                    primaryButtonSize = 70.dp
                )
            }
        }
    }
}
