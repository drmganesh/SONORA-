package ui.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PlaybackInfo
import com.example.model.RepeatMode
import com.example.model.ShuffleMode
import com.example.model.Song
import com.example.ui.components.ArtworkAlbumView
import com.example.ui.theme.SonoraCharcoal
import com.example.ui.theme.SonoraCharcoalBorder
import com.example.ui.theme.SonoraDustyRose
import com.example.ui.theme.SonoraTextMuted
import com.example.ui.theme.SonoraTextPrimary
import com.example.ui.theme.SonoraTextSecondary

/**
 * Style 3: Editorial
 * Magazine-inspired two-tone layout.
 * Upper section: Framed high-fashion artwork with issue stamp.
 * Lower section: Contrasting charcoal container with high-contrast editorial typography and minimal luxury playback controls.
 */
@OptIn(ExperimentalFoundationApi::class)
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
    onNavigateToEqualizer: () -> Unit,
    onArtworkLongPress: () -> Unit,
    seekbarStyle: ui.player.seekbar.SeekbarStyle = ui.player.seekbar.SeekbarStyle.DEFAULT,
    modifier: Modifier = Modifier
) {
    val duration = (playbackInfo.durationMs).coerceAtLeast(song?.durationMs ?: 0L)

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(Color.Black)
    ) {
        // Upper Section: Magazine Header & Framed Artwork
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.1f)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Editorial Masthead Navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse", tint = SonoraTextPrimary, modifier = Modifier.size(26.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "SONORA GAZETTE • ISSUE 04",
                        color = SonoraDustyRose,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "THE SOUNDTRACK COLLECTION",
                        color = SonoraTextMuted,
                        fontSize = 9.sp,
                        letterSpacing = 1.sp
                    )
                }
                Row {
                    IconButton(onClick = onNavigateToEqualizer, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.GraphicEq, contentDescription = "Equalizer", tint = SonoraTextSecondary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onOpenQueue, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue", tint = SonoraTextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Framed Editorial Artwork
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, SonoraCharcoalBorder, RoundedCornerShape(16.dp))
                    .combinedClickable(
                        onClick = onTogglePlayPause,
                        onLongClick = onArtworkLongPress
                    ),
                contentAlignment = Alignment.Center
            ) {
                ArtworkAlbumView(
                    song = song,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 16.dp,
                    isPlaying = playbackInfo.isPlaying
                )

                // Editorial Corner Badge Stamp
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, SonoraDustyRose.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "PLATE N° 01 • HIGH FIDELITY",
                        color = SonoraDustyRose,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Lower Section: Contrasting Dark Charcoal Magazine Block
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.9f)
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)),
            color = SonoraCharcoal,
            tonalElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, SonoraCharcoalBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Editorial Typography
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "FEATURED SELECTION",
                            color = SonoraDustyRose,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        IconButton(onClick = onOpenLyrics, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Lyrics, contentDescription = "Lyrics", tint = SonoraDustyRose, modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(
                        text = song?.title ?: "No Song Selected",
                        color = SonoraTextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song?.artist ?: "Unknown Artist",
                        color = SonoraTextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Dynamic Seekbar with Selected Style
                ui.player.seekbar.SeekbarRenderer(
                    currentPositionMs = playbackInfo.currentPositionMs,
                    durationMs = duration,
                    onSeekTo = onSeekTo,
                    isPlaying = playbackInfo.isPlaying,
                    style = seekbarStyle,
                    modifier = Modifier.fillMaxWidth()
                )

                // Luxury Minimalist Transport Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onToggleShuffle, modifier = Modifier.size(44.dp)) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (playbackInfo.shuffleMode == ShuffleMode.ON) SonoraDustyRose else SonoraTextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onPlayPrevious, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = SonoraTextPrimary, modifier = Modifier.size(30.dp))
                        }

                        Surface(
                            onClick = onTogglePlayPause,
                            modifier = Modifier.size(62.dp),
                            shape = CircleShape,
                            color = SonoraDustyRose,
                            shadowElevation = 6.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (playbackInfo.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.Black,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        IconButton(onClick = onPlayNext, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = SonoraTextPrimary, modifier = Modifier.size(30.dp))
                        }
                    }

                    IconButton(onClick = onCycleRepeat, modifier = Modifier.size(44.dp)) {
                        val icon = if (playbackInfo.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                        val tint = if (playbackInfo.repeatMode != RepeatMode.OFF) SonoraDustyRose else SonoraTextMuted
                        Icon(icon, contentDescription = "Repeat", tint = tint, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
