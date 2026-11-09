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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
 * Style 5: Bento
 * Modern Bento grid layout with distinct rounded rectangular sections for
 * album artwork, track information, scrubber progress, and controls.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BentoPlayer(
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
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Minimal Navigation Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(42.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse", tint = SonoraTextPrimary, modifier = Modifier.size(26.dp))
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = SonoraCharcoal,
                border = androidx.compose.foundation.BorderStroke(1.dp, SonoraCharcoalBorder)
            ) {
                Text(
                    text = "BENTO GRID ARCHITECTURE",
                    color = SonoraDustyRose,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            IconButton(onClick = onOpenQueue, modifier = Modifier.size(42.dp)) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue", tint = SonoraTextSecondary, modifier = Modifier.size(20.dp))
            }
        }

        // BENTO TILE 1: Main Artwork Tile
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.2f)
                .clip(RoundedCornerShape(26.dp))
                .combinedClickable(
                    onClick = onTogglePlayPause,
                    onLongClick = onArtworkLongPress
                ),
            color = SonoraCharcoal,
            tonalElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, SonoraCharcoalBorder)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                ArtworkAlbumView(
                    song = song,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 20.dp,
                    isPlaying = playbackInfo.isPlaying
                )
            }
        }

        // BENTO ROW 2: Split Info Tile & Mode Tile
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Track Info Bento Tile
            Surface(
                modifier = Modifier
                    .weight(1.4f)
                    .clip(RoundedCornerShape(22.dp)),
                color = SonoraCharcoal,
                tonalElevation = 4.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, SonoraCharcoalBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "NOW PLAYING",
                        color = SonoraDustyRose,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = song?.title ?: "No Song Selected",
                        color = SonoraTextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song?.artist ?: "Unknown Artist",
                        color = SonoraTextSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Quick Actions Bento Tile (Lyrics & EQ)
            Surface(
                modifier = Modifier
                    .weight(0.8f)
                    .clip(RoundedCornerShape(22.dp)),
                color = SonoraCharcoal,
                tonalElevation = 4.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, SonoraCharcoalBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onOpenLyrics, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Lyrics, contentDescription = "Lyrics", tint = SonoraDustyRose, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onNavigateToEqualizer, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.GraphicEq, contentDescription = "Equalizer", tint = SonoraTextSecondary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // BENTO TILE 3: Progress & Scrubber Tile
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp)),
            color = SonoraCharcoal,
            tonalElevation = 4.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, SonoraCharcoalBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                ui.player.seekbar.SeekbarRenderer(
                    currentPositionMs = playbackInfo.currentPositionMs,
                    durationMs = duration,
                    onSeekTo = onSeekTo,
                    isPlaying = playbackInfo.isPlaying,
                    style = seekbarStyle,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // BENTO ROW 4: Playback Controls & Shuffle/Repeat Tiles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Main Transport Bento Tile
            Surface(
                modifier = Modifier
                    .weight(1.5f)
                    .clip(RoundedCornerShape(22.dp)),
                color = SonoraCharcoal,
                tonalElevation = 6.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, SonoraCharcoalBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onPlayPrevious, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = SonoraTextPrimary, modifier = Modifier.size(28.dp))
                    }

                    Surface(
                        onClick = onTogglePlayPause,
                        modifier = Modifier.size(54.dp),
                        shape = CircleShape,
                        color = SonoraDustyRose,
                        shadowElevation = 6.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (playbackInfo.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.Black,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    IconButton(onClick = onPlayNext, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = SonoraTextPrimary, modifier = Modifier.size(28.dp))
                    }
                }
            }

            // Shuffle & Repeat Bento Tile
            Surface(
                modifier = Modifier
                    .weight(0.7f)
                    .clip(RoundedCornerShape(22.dp)),
                color = SonoraCharcoal,
                tonalElevation = 4.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, SonoraCharcoalBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onToggleShuffle, modifier = Modifier.size(38.dp)) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (playbackInfo.shuffleMode == ShuffleMode.ON) SonoraDustyRose else SonoraTextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onCycleRepeat, modifier = Modifier.size(38.dp)) {
                        val icon = if (playbackInfo.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                        val tint = if (playbackInfo.repeatMode != RepeatMode.OFF) SonoraDustyRose else SonoraTextMuted
                        Icon(icon, contentDescription = "Repeat", tint = tint, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
