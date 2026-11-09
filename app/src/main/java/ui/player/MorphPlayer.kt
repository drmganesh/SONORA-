package ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Brush
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
import kotlin.math.sin

/**
 * Style 7: Morph
 * Animated organic artwork container.
 * Album artwork displayed inside a breathing, morphing shape with smooth
 * organic radius transformations, reactive scaling, and sleek floating controls.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MorphPlayer(
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

    // Breathing Morph Animation
    val infiniteTransition = rememberInfiniteTransition(label = "morphTransition")
    val morphProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = AnimRepeatMode.Reverse
        ),
        label = "morphProgress"
    )

    val breathingScale = if (playbackInfo.isPlaying) 1f + (sin(morphProgress * Math.PI.toFloat()) * 0.04f) else 1f
    val cornerTopStart = (28 + (morphProgress * 24)).dp
    val cornerTopEnd = (52 - (morphProgress * 24)).dp
    val cornerBottomEnd = (32 + (morphProgress * 20)).dp
    val cornerBottomStart = (48 - (morphProgress * 20)).dp

    val morphShape = RoundedCornerShape(
        topStart = cornerTopStart,
        topEnd = cornerTopEnd,
        bottomEnd = cornerBottomEnd,
        bottomStart = cornerBottomStart
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse", tint = SonoraTextPrimary, modifier = Modifier.size(28.dp))
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SonoraCharcoal,
                border = androidx.compose.foundation.BorderStroke(1.dp, SonoraCharcoalBorder)
            ) {
                Text(
                    text = "ORGANIC MORPH",
                    color = SonoraDustyRose,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
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

        // Breathing Morph Container
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .aspectRatio(1f)
                .scale(breathingScale)
                .shadow(
                    elevation = if (playbackInfo.isPlaying) 20.dp else 8.dp,
                    shape = morphShape,
                    ambientColor = SonoraDustyRose.copy(alpha = 0.4f),
                    spotColor = SonoraDustyRose.copy(alpha = 0.5f)
                )
                .clip(morphShape)
                .border(2.dp, SonoraDustyRose.copy(alpha = 0.6f), morphShape)
                .combinedClickable(
                    onClick = onTogglePlayPause,
                    onLongClick = onArtworkLongPress
                ),
            contentAlignment = Alignment.Center
        ) {
            ArtworkAlbumView(
                song = song,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 0.dp,
                isPlaying = playbackInfo.isPlaying
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Track Information
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = song?.title ?: "No Song Selected",
                color = SonoraTextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = song?.artist ?: "Unknown Artist",
                color = SonoraDustyRose,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
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

        // Floating Transport Controls
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp)),
            color = SonoraCharcoal,
            border = androidx.compose.foundation.BorderStroke(1.dp, SonoraCharcoalBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
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

                IconButton(onClick = onPlayPrevious, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = SonoraTextPrimary, modifier = Modifier.size(30.dp))
                }

                Surface(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(60.dp),
                    shape = CircleShape,
                    color = SonoraDustyRose,
                    shadowElevation = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (playbackInfo.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.Black,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                IconButton(onClick = onPlayNext, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = SonoraTextPrimary, modifier = Modifier.size(30.dp))
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
