package ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
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
 * Style 6: Sticker
 * Irregular die-cut sticker-inspired album artwork presentation.
 * Organic die-cut borders, subtle organic tilt, tactile drop shadows, and spring-physics controls.
 */
@OptIn(ExperimentalFoundationApi::class)
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
            .padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse", tint = SonoraTextPrimary, modifier = Modifier.size(28.dp))
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SonoraCharcoal,
                border = androidx.compose.foundation.BorderStroke(1.dp, SonoraCharcoalBorder)
            ) {
                Text(
                    text = "STICKER DIE-CUT",
                    color = SonoraDustyRose,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            Row {
                IconButton(onClick = onNavigateToEqualizer, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.GraphicEq, contentDescription = "Equalizer", tint = SonoraTextSecondary, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onOpenQueue, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue", tint = SonoraTextSecondary, modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Die-cut Sticker Container with slight organic tilt & thick die-cut border
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .aspectRatio(1f)
                .rotate(-2.5f)
                .shadow(16.dp, RoundedCornerShape(32.dp), spotColor = SonoraDustyRose.copy(alpha = 0.3f))
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xFFE8DFDD))
                .padding(5.dp) // Die-cut white border
                .combinedClickable(
                    onClick = onTogglePlayPause,
                    onLongClick = onArtworkLongPress
                ),
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

                // Die-cut Sticker Pill Badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(14.dp)
                        .rotate(4f),
                    shape = RoundedCornerShape(10.dp),
                    color = SonoraDustyRose,
                    shadowElevation = 6.dp
                ) {
                    Text(
                        text = "★ SONORA SOUND",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Song Information Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp)),
            color = SonoraCharcoal,
            border = androidx.compose.foundation.BorderStroke(1.dp, SonoraCharcoalBorder)
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
                        color = SonoraTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song?.artist ?: "Unknown Artist",
                        color = SonoraTextSecondary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onOpenLyrics, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Lyrics, contentDescription = "Lyrics", tint = SonoraDustyRose, modifier = Modifier.size(22.dp))
                }
            }
        }

        // Dynamic Seekbar
        ui.player.seekbar.SeekbarRenderer(
            currentPositionMs = playbackInfo.currentPositionMs,
            durationMs = duration,
            onSeekTo = onSeekTo,
            isPlaying = playbackInfo.isPlaying,
            style = seekbarStyle,
            modifier = Modifier.fillMaxWidth()
        )

        // Bouncy Spring Playback Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleShuffle, modifier = Modifier.size(46.dp)) {
                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (playbackInfo.shuffleMode == ShuffleMode.ON) SonoraDustyRose else SonoraTextMuted,
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(onClick = onPlayPrevious, modifier = Modifier.size(50.dp)) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = SonoraTextPrimary, modifier = Modifier.size(30.dp))
            }

            // Play/Pause with Spring Physics Scale
            val playInteractionSource = remember { MutableInteractionSource() }
            val isPlayPressed by playInteractionSource.collectIsPressedAsState()
            val playScale by animateFloatAsState(
                targetValue = if (isPlayPressed) 0.88f else 1f,
                animationSpec = spring(dampingRatio = 0.4f, stiffness = 400f),
                label = "playScale"
            )

            Surface(
                onClick = onTogglePlayPause,
                interactionSource = playInteractionSource,
                modifier = Modifier
                    .size(66.dp)
                    .scale(playScale),
                shape = CircleShape,
                color = SonoraDustyRose,
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (playbackInfo.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            IconButton(onClick = onPlayNext, modifier = Modifier.size(50.dp)) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = SonoraTextPrimary, modifier = Modifier.size(30.dp))
            }

            IconButton(onClick = onCycleRepeat, modifier = Modifier.size(46.dp)) {
                val icon = if (playbackInfo.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                val tint = if (playbackInfo.repeatMode != RepeatMode.OFF) SonoraDustyRose else SonoraTextMuted
                Icon(icon, contentDescription = "Repeat", tint = tint, modifier = Modifier.size(22.dp))
            }
        }
    }
}
