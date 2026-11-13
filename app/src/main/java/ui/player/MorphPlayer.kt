package ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AnimationIntensity
import com.example.model.PlaybackInfo
import com.example.model.PlayerAppearanceConfig
import com.example.model.PlayerGestureConfig
import com.example.model.Song
import com.example.ui.components.ArtworkAlbumView
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import kotlin.math.sin
import ui.components.PlaybackControls
import ui.player.seekbar.SeekbarRenderer
import ui.player.seekbar.SeekbarStyle
import ui.theme.LocalEditorialColors
import ui.theme.LocalReduceAnimations

/**
 * Style 7: Morph
 * Organic morphing artwork container that honors LocalReduceAnimations and LocalEditorialColors.
 */
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
    val reduceAnimations = LocalReduceAnimations.current || appearance.animationIntensity == AnimationIntensity.OFF
    val duration = (playbackInfo.durationMs).coerceAtLeast(song?.durationMs ?: 0L)
    val scale = appearance.typographyScale.coerceIn(0.85f, 1.25f)

    val infiniteTransition = rememberInfiniteTransition(label = "morphTransition")
    val animatedMorph by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = AnimRepeatMode.Reverse
        ),
        label = "morphProgress"
    )
    val morphProgress = if (reduceAnimations || !playbackInfo.isPlaying) 0.35f else animatedMorph

    val breathingScale = if (playbackInfo.isPlaying && !reduceAnimations) {
        1f + (sin(morphProgress * Math.PI.toFloat()) * 0.035f * appearance.animationIntensity.multiplier)
    } else 1f
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
                    shape = RoundedCornerShape(12.dp),
                    color = colors.accentPill,
                    border = BorderStroke(1.dp, colors.cardBorder)
                ) {
                    Text(
                        text = "ORGANIC MORPH",
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

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth(appearance.artworkSize.fraction)
                    .aspectRatio(1f)
                    .graphicsLayer {
                        val combined = breathingScale * gestureController.artworkZoomScale
                        scaleX = combined
                        scaleY = combined
                    }
                    .shadow(
                        elevation = if (playbackInfo.isPlaying) 18.dp else 8.dp,
                        shape = morphShape,
                        ambientColor = colors.shadowColor,
                        spotColor = colors.shadowColor
                    )
                    .clip(morphShape)
                    .border(2.dp, colors.accentStrong.copy(alpha = 0.65f), morphShape)
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
                ArtworkAlbumView(
                    song = song,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 0.dp,
                    isPlaying = playbackInfo.isPlaying
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = song?.title ?: "No Song Selected",
                    fontFamily = OutfitFontFamily,
                    color = colors.textPrimary,
                    fontSize = (22f * scale).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (appearance.showAlbumMetadata && !song?.album.isNullOrBlank()) {
                        "${song?.artist ?: "Unknown Artist"} • ${song?.album}"
                    } else {
                        song?.artist ?: "Unknown Artist"
                    },
                    fontFamily = PlusJakartaFontFamily,
                    color = colors.accentStrong,
                    fontSize = (15f * scale).sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
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

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp)),
                color = colors.surface,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, colors.cardBorder)
            ) {
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
                    primaryButtonSize = 60.dp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
    }
}
