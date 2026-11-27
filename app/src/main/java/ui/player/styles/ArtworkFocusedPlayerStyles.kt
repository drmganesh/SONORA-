package ui.player.styles

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.example.ui.components.ArtworkAlbumView
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import ui.components.AdaptiveSeekbar
import ui.components.ArtworkContainer
import ui.components.PlaybackControls
import ui.components.TrackMetadataBar
import ui.player.NowPlayingTopHeader
import ui.player.PlayerGestureController
import ui.player.PlayerRenderState
import ui.player.PlayerStyleCallbacks
import ui.player.PlayerStyleManager
import ui.theme.LocalEditorialColors
import ui.theme.LocalReduceAnimations
import kotlin.math.cos
import kotlin.math.sin

/**
 * Group B: Album Artwork Focused Now Playing Styles
 * - Floating Artwork
 * - Cassette Player (animated tape reels)
 * - CD Player (spinning reflective optical disc)
 * - Retro Walkman (brushed metal deck + LCD counter + tactile keys)
 * - Artwork Carousel (swipeable prev / current / next album covers)
 */

@Composable
fun FloatingArtworkPlayer(
    state: PlayerRenderState,
    callbacks: PlayerStyleCallbacks,
    gestureController: PlayerGestureController,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val palette = state.dynamicPalette
    val song = state.song
    val duration = state.playbackInfo.durationMs.coerceAtLeast(song?.durationMs ?: 0L)
    val accent = PlayerStyleManager.resolveAccentColor(state.appearance, colors, palette)
    val reduceAnim = LocalReduceAnimations.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val shouldAnimate = lifecycleState.isAtLeast(Lifecycle.State.STARTED) &&
        PlayerStyleManager.shouldRunAmbientAnimation(state, reduceAnim)

    val floatOffsetDp = if (shouldAnimate) {
        val transition = rememberInfiniteTransition(label = "floatingArtworkLevitation")
        val value by transition.animateFloat(
            initialValue = -5f,
            targetValue = 5f,
            animationSpec = infiniteRepeatable(
                animation = tween(3200),
                repeatMode = RepeatMode.Reverse
            ),
            label = "floatY"
        )
        value
    } else 0f

    Box(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .testTag("player_style_floating_artwork"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(horizontal = 24.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            NowPlayingTopHeader(
                song = song,
                onClose = callbacks.onClose,
                onOpenQueue = callbacks.onOpenQueue,
                onOpenLyrics = callbacks.onOpenLyrics,
                onOpenSleepTimer = callbacks.onOpenSleepTimer,
                onOpenStyleWheel = callbacks.onArtworkLongPress,
                onToggleVisualizer = callbacks.onToggleVisualizer,
                isVisualizerActive = state.isVisualizerActive,
                onOpenEqualizer = callbacks.onOpenEqualizer,
                onOpenAudioStudio = callbacks.onOpenAudioEffects,
                onOpenOutputDevice = callbacks.onOpenOutputDevice,
                onToggleFocusMode = callbacks.onToggleFocusMode,
                onOpenCustomization = callbacks.onOpenCustomization
            )

            AdaptiveHeroSlot(
                widthFraction = state.appearance.artworkSize.fraction.coerceIn(0.76f, 0.92f)
            ) {
                // Soft ambient colored drop-glow underneath floating square
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 24.dp)
                ) {
                    drawOval(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                palette.primary.copy(alpha = if (colors.isDark) 0.52f else 0.34f),
                                Color.Transparent
                            ),
                            center = Offset(size.width / 2f, size.height * 0.88f),
                            radius = size.width * 0.56f
                        ),
                        topLeft = Offset(size.width * 0.06f, size.height * 0.72f),
                        size = Size(size.width * 0.88f, size.height * 0.28f)
                    )
                }

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
                    modifier = Modifier
                        .fillMaxSize(0.92f)
                        .graphicsLayer { translationY = floatOffsetDp.dp.toPx() }
                        .shadow(
                            elevation = 24.dp,
                            shape = RoundedCornerShape(PlayerStyleManager.resolveCornerRadius(state.appearance, 26.dp)),
                            ambientColor = palette.primary,
                            spotColor = palette.primary
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                TrackMetadataBar(
                    song = song,
                    appearance = state.appearance,
                    onToggleFavorite = callbacks.onToggleFavorite,
                    onOpenLyrics = callbacks.onOpenLyrics,
                    onOpenQueue = callbacks.onOpenQueue,
                    accentColor = accent
                )

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

                PlaybackControls(
                    playbackInfo = state.playbackInfo,
                    onTogglePlayPause = callbacks.onTogglePlayPause,
                    onPlayNext = callbacks.onPlayNext,
                    onPlayPrevious = callbacks.onPlayPrevious,
                    onToggleShuffle = callbacks.onToggleShuffle,
                    onCycleRepeat = callbacks.onCycleRepeat,
                    appearance = state.appearance,
                    accentColor = accent,
                    primaryButtonSize = 70.dp
                )
            }
        }
    }
}

@Composable
fun CassettePlayer(
    state: PlayerRenderState,
    callbacks: PlayerStyleCallbacks,
    gestureController: PlayerGestureController,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val palette = state.dynamicPalette
    val song = state.song
    val duration = state.playbackInfo.durationMs.coerceAtLeast(song?.durationMs ?: 0L)
    val accent = PlayerStyleManager.resolveAccentColor(state.appearance, colors, palette)
    val reduceAnim = LocalReduceAnimations.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val isSpinning = lifecycleState.isAtLeast(Lifecycle.State.STARTED) &&
        PlayerStyleManager.shouldRunAmbientAnimation(state, reduceAnim)

    val reelAngle = if (isSpinning) {
        val transition = rememberInfiniteTransition(label = "cassetteReelSpin")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "reelRotation"
        )
        value
    } else 0f

    val progress = if (duration > 0L) {
        (state.playbackInfo.currentPositionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else 0.35f

    Box(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .testTag("player_style_cassette"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            NowPlayingTopHeader(
                song = song,
                onClose = callbacks.onClose,
                onOpenQueue = callbacks.onOpenQueue,
                onOpenLyrics = callbacks.onOpenLyrics,
                onOpenSleepTimer = callbacks.onOpenSleepTimer,
                onOpenStyleWheel = callbacks.onArtworkLongPress,
                onToggleVisualizer = callbacks.onToggleVisualizer,
                isVisualizerActive = state.isVisualizerActive,
                onOpenEqualizer = callbacks.onOpenEqualizer,
                onOpenAudioStudio = callbacks.onOpenAudioEffects,
                onOpenOutputDevice = callbacks.onOpenOutputDevice,
                onToggleFocusMode = callbacks.onToggleFocusMode,
                onOpenCustomization = callbacks.onOpenCustomization
            )

            // Realistic Cassette Shell + Animated Dual Tape Reels
            AdaptiveHeroSlot(
                widthFraction = 1f,
                aspectRatio = 1.54f
            ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF1F2128),
                border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.14f)),
                shadowElevation = 14.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { callbacks.onTogglePlayPause() }
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                    // Cassette paper label upper area
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFFF6EFE2),
                                        Color(0xFFE9DFCE)
                                    )
                                )
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                ArtworkAlbumView(
                                    song = song,
                                    modifier = Modifier.size(34.dp),
                                    cornerRadius = 6.dp,
                                    isPlaying = state.playbackInfo.isPlaying
                                )
                                Column {
                                    Text(
                                        text = (song?.title ?: "${domain.branding.AppBranding.APP_NAME} TAPE").uppercase(),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp,
                                        color = Color(0xFF1A1B20),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = song?.artist ?: "TYPE II CHROME • 90 MIN",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 10.sp,
                                        color = Color(0xFF4A4D57),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = accent
                            ) {
                                Text(
                                    text = "SIDE A",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Color stripe across cassette label
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(listOf(palette.primary, accent, palette.secondary))
                                )
                        )

                        // Smoked transparent tape reel window
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF121318))
                                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val cy = size.height / 2f
                                val leftCx = size.width * 0.28f
                                val rightCx = size.width * 0.72f
                                val maxTapeR = size.height * 0.42f
                                val hubR = size.height * 0.21f
                                val leftTapeR = hubR + (maxTapeR - hubR) * (1f - progress * 0.72f)
                                val rightTapeR = hubR + (maxTapeR - hubR) * (0.28f + progress * 0.72f)

                                // Magnetic tape ribbon connecting both spools
                                drawLine(
                                    color = Color(0xFF3E2B22),
                                    start = Offset(leftCx, cy + leftTapeR * 0.85f),
                                    end = Offset(rightCx, cy + rightTapeR * 0.85f),
                                    strokeWidth = 3.dp.toPx()
                                )

                                // Left & right wound tape packs
                                drawCircle(color = Color(0xFF2C1E18), radius = leftTapeR, center = Offset(leftCx, cy))
                                drawCircle(color = Color(0xFF2C1E18), radius = rightTapeR, center = Offset(rightCx, cy))

                                // Draw spinning spokes on both reels
                                listOf(Offset(leftCx, cy), Offset(rightCx, cy)).forEach { center ->
                                    drawCircle(color = Color(0xFFEBEFF5), radius = hubR, center = center)
                                    drawCircle(color = Color(0xFF15161B), radius = hubR * 0.56f, center = center)
                                    rotate(degrees = reelAngle, pivot = center) {
                                        for (spoke in 0 until 6) {
                                            val rad = Math.toRadians(spoke * 60.0)
                                            val start = Offset(
                                                center.x + (cos(rad) * hubR * 0.35f).toFloat(),
                                                center.y + (sin(rad) * hubR * 0.35f).toFloat()
                                            )
                                            val end = Offset(
                                                center.x + (cos(rad) * hubR * 0.92f).toFloat(),
                                                center.y + (sin(rad) * hubR * 0.92f).toFloat()
                                            )
                                            drawLine(
                                                color = accent,
                                                start = start,
                                                end = end,
                                                strokeWidth = 2.dp.toPx(),
                                                cap = StrokeCap.Round
                                            )
                                        }
                                    }
                                }

                                // Center level ticks
                                for (tick in 0..4) {
                                    val x = size.width * (0.42f + tick * 0.04f)
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.25f),
                                        start = Offset(x, size.height * 0.22f),
                                        end = Offset(x, size.height * 0.36f),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                }
                            }
                        }

                        // Lower cassette magnetic head notch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "DOLBY NR • HI-RES",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                color = Color(0xFF5A5E68)
                            )
                            Text(
                                text = if (state.playbackInfo.isPlaying) "PLAYING ►" else "STOPPED ■",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                color = accent
                            )
                        }
                    }
                }
            }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TrackMetadataBar(
                    song = song,
                    appearance = state.appearance,
                    onToggleFavorite = callbacks.onToggleFavorite,
                    onOpenLyrics = callbacks.onOpenLyrics,
                    onOpenQueue = callbacks.onOpenQueue,
                    accentColor = accent
                )

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

                PlaybackControls(
                    playbackInfo = state.playbackInfo,
                    onTogglePlayPause = callbacks.onTogglePlayPause,
                    onPlayNext = callbacks.onPlayNext,
                    onPlayPrevious = callbacks.onPlayPrevious,
                    onToggleShuffle = callbacks.onToggleShuffle,
                    onCycleRepeat = callbacks.onCycleRepeat,
                    appearance = state.appearance,
                    accentColor = accent,
                    primaryButtonSize = 68.dp
                )
            }
        }
    }
}

@Composable
fun CdPlayer(
    state: PlayerRenderState,
    callbacks: PlayerStyleCallbacks,
    gestureController: PlayerGestureController,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val palette = state.dynamicPalette
    val song = state.song
    val duration = state.playbackInfo.durationMs.coerceAtLeast(song?.durationMs ?: 0L)
    val accent = PlayerStyleManager.resolveAccentColor(state.appearance, colors, palette)
    val reduceAnim = LocalReduceAnimations.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val isSpinning = lifecycleState.isAtLeast(Lifecycle.State.STARTED) &&
        PlayerStyleManager.shouldRunAmbientAnimation(state, reduceAnim)

    val sheenRotation = if (isSpinning) {
        val transition = rememberInfiniteTransition(label = "cdOpticalSpin")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 8000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "cdRotation"
        )
        value
    } else 25f

    Box(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .testTag("player_style_cd_player"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            NowPlayingTopHeader(
                song = song,
                onClose = callbacks.onClose,
                onOpenQueue = callbacks.onOpenQueue,
                onOpenLyrics = callbacks.onOpenLyrics,
                onOpenSleepTimer = callbacks.onOpenSleepTimer,
                onOpenStyleWheel = callbacks.onArtworkLongPress,
                onToggleVisualizer = callbacks.onToggleVisualizer,
                isVisualizerActive = state.isVisualizerActive,
                onOpenEqualizer = callbacks.onOpenEqualizer,
                onOpenAudioStudio = callbacks.onOpenAudioEffects,
                onOpenOutputDevice = callbacks.onOpenOutputDevice,
                onToggleFocusMode = callbacks.onToggleFocusMode,
                onOpenCustomization = callbacks.onOpenCustomization
            )

            // Jewel-case tray with spinning reflective optical CD
            AdaptiveHeroSlot(
                widthFraction = state.appearance.artworkSize.fraction.coerceIn(0.76f, 0.92f)
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
                    isCircular = true,
                    forceVinylSpin = true,
                    modifier = Modifier
                        .fillMaxSize(0.94f)
                        .border(2.dp, Color.White.copy(alpha = 0.48f), CircleShape),
                    overlayContent = {
                        // Prismatic optical rainbow sheen + polycarbonate center clamp
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val r = size.minDimension / 2f
                            rotate(degrees = sheenRotation, pivot = center) {
                                drawCircle(
                                    brush = Brush.sweepGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color(0x55FFFFFF),
                                            Color(0x4480E8FF),
                                            Color(0x44F48BFF),
                                            Color(0x44FFE27A),
                                            Color.Transparent,
                                            Color(0x55FFFFFF),
                                            Color.Transparent
                                        ),
                                        center = center
                                    ),
                                    radius = r,
                                    center = center
                                )
                            }
                            // Metallic mirror clamp ring
                            drawCircle(
                                color = Color(0xFFD8DEE9).copy(alpha = 0.82f),
                                radius = r * 0.28f,
                                center = center,
                                style = Stroke(width = 6.dp.toPx())
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.35f),
                                radius = r * 0.22f,
                                center = center
                            )
                            // Center spindle hole
                            drawCircle(
                                color = colors.background,
                                radius = r * 0.14f,
                                center = center
                            )
                            drawCircle(
                                color = accent,
                                radius = r * 0.14f,
                                center = center,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TrackMetadataBar(
                    song = song,
                    appearance = state.appearance,
                    onToggleFavorite = callbacks.onToggleFavorite,
                    onOpenLyrics = callbacks.onOpenLyrics,
                    onOpenQueue = callbacks.onOpenQueue,
                    accentColor = accent
                )

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

                PlaybackControls(
                    playbackInfo = state.playbackInfo,
                    onTogglePlayPause = callbacks.onTogglePlayPause,
                    onPlayNext = callbacks.onPlayNext,
                    onPlayPrevious = callbacks.onPlayPrevious,
                    onToggleShuffle = callbacks.onToggleShuffle,
                    onCycleRepeat = callbacks.onCycleRepeat,
                    appearance = state.appearance,
                    accentColor = accent,
                    primaryButtonSize = 68.dp
                )
            }
        }
    }
}

@Composable
fun RetroWalkmanPlayer(
    state: PlayerRenderState,
    callbacks: PlayerStyleCallbacks,
    gestureController: PlayerGestureController,
    modifier: Modifier = Modifier
) {
    val song = state.song
    val duration = state.playbackInfo.durationMs.coerceAtLeast(song?.durationMs ?: 0L)
    val reduceAnim = LocalReduceAnimations.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val isSpinning = lifecycleState.isAtLeast(Lifecycle.State.STARTED) &&
        PlayerStyleManager.shouldRunAmbientAnimation(state, reduceAnim)

    val reelAngle = if (isSpinning) {
        val transition = rememberInfiniteTransition(label = "walkmanReelSpin")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "walkmanReel"
        )
        value
    } else 0f

    val chassisBg = Brush.verticalGradient(
        colors = listOf(Color(0xFF333945), Color(0xFF232730), Color(0xFF1A1D24))
    )
    val amberLcdBg = Color(0xFF2B2412)
    val amberLcdInk = Color(0xFFFFC145)
    val orangeAccent = Color(0xFFFF6D2D)

    val posSec = (state.playbackInfo.currentPositionMs / 1000L).coerceAtLeast(0L)
    val durSec = (duration / 1000L).coerceAtLeast(0L)
    val lcdCounter = String.format(
        java.util.Locale.US,
        "%02d:%02d / %02d:%02d",
        posSec / 60, posSec % 60, durSec / 60, durSec % 60
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(chassisBg)
            .navigationBarsPadding()
            .testTag("player_style_retro_walkman"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(horizontal = 18.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            NowPlayingTopHeader(
                song = song,
                onClose = callbacks.onClose,
                onOpenQueue = callbacks.onOpenQueue,
                onOpenLyrics = callbacks.onOpenLyrics,
                onOpenSleepTimer = callbacks.onOpenSleepTimer,
                onOpenStyleWheel = callbacks.onArtworkLongPress,
                onToggleVisualizer = callbacks.onToggleVisualizer,
                isVisualizerActive = state.isVisualizerActive,
                onOpenEqualizer = callbacks.onOpenEqualizer,
                onOpenAudioStudio = callbacks.onOpenAudioEffects,
                onOpenOutputDevice = callbacks.onOpenOutputDevice,
                onToggleFocusMode = callbacks.onToggleFocusMode,
                onOpenCustomization = callbacks.onOpenCustomization
            )

            // Walkman Metal Body Frame
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Color(0xFF2B313C),
                border = BorderStroke(1.5.dp, Color(0xFF555E6F)),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Branding strip + Orange LED
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${domain.branding.AppBranding.APP_NAME} WALKMAN  WM-88",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            letterSpacing = 1.6.sp,
                            color = Color(0xFFDDE3EE)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (state.playbackInfo.isPlaying) orangeAccent else Color(0xFF5A4035))
                            )
                            Text(
                                text = "BATT OK",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = Color(0xFFA7B0C0)
                            )
                        }
                    }

                    // Recessed Amber LCD Readout
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = amberLcdBg,
                        border = BorderStroke(1.dp, Color(0xFF6B5726)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "TRK ${(state.queueIndex + 1).toString().padStart(2, '0')}  MEGA BASS",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = amberLcdInk.copy(alpha = 0.85f)
                                )
                                Text(
                                    text = lcdCounter,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = amberLcdInk
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${song?.title ?: "INSERT CASSETTE"} — ${song?.artist ?: domain.branding.AppBranding.APP_NAME}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = amberLcdInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Viewing Window with Album Cover + Spinning Mini Spools
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF14171D))
                            .border(1.5.dp, Color(0xFF474F5E), RoundedCornerShape(14.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ArtworkAlbumView(
                            song = song,
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(1f),
                            cornerRadius = 10.dp,
                            isPlaying = state.playbackInfo.isPlaying
                        )
                        Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            val cy = size.height / 2f
                            val r = size.minDimension * 0.28f
                            val left = Offset(size.width * 0.28f, cy)
                            val right = Offset(size.width * 0.72f, cy)
                            drawLine(
                                color = Color(0xFF553B2F),
                                start = Offset(left.x, cy + r),
                                end = Offset(right.x, cy + r),
                                strokeWidth = 2.5.dp.toPx()
                            )
                            listOf(left, right).forEach { c ->
                                drawCircle(color = Color(0xFF333944), radius = r * 1.25f, center = c)
                                drawCircle(color = Color(0xFFE7ECF4), radius = r * 0.62f, center = c)
                                rotate(reelAngle, c) {
                                    for (i in 0..2) {
                                        val rad = Math.toRadians(i * 120.0)
                                        drawLine(
                                            color = orangeAccent,
                                            start = c,
                                            end = Offset(
                                                c.x + (cos(rad) * r * 0.56f).toFloat(),
                                                c.y + (sin(rad) * r * 0.56f).toFloat()
                                            ),
                                            strokeWidth = 2.dp.toPx(),
                                            cap = StrokeCap.Round
                                        )
                                    }
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
                        accentColor = orangeAccent,
                        inactiveColor = Color.White.copy(alpha = 0.2f)
                    )
                }
            }

            // Chunky Mechanical Walkman Transport Keys
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                WalkmanMechanicalKey(
                    icon = Icons.Rounded.SkipPrevious,
                    label = "PREV",
                    isPrimary = false,
                    accent = orangeAccent,
                    onClick = callbacks.onPlayPrevious,
                    modifier = Modifier.weight(1f).testTag("previous_button")
                )
                WalkmanMechanicalKey(
                    icon = Icons.Rounded.FastRewind,
                    label = "-10S",
                    isPrimary = false,
                    accent = orangeAccent,
                    onClick = { callbacks.onSeekTo((state.playbackInfo.currentPositionMs - 10_000L).coerceAtLeast(0L)) },
                    modifier = Modifier.weight(1f)
                )
                WalkmanMechanicalKey(
                    icon = if (state.playbackInfo.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    label = if (state.playbackInfo.isPlaying) "PAUSE" else "PLAY",
                    isPrimary = true,
                    accent = orangeAccent,
                    onClick = callbacks.onTogglePlayPause,
                    modifier = Modifier.weight(1.35f).testTag("play_pause_button")
                )
                WalkmanMechanicalKey(
                    icon = Icons.Rounded.FastForward,
                    label = "+10S",
                    isPrimary = false,
                    accent = orangeAccent,
                    onClick = { callbacks.onSeekTo((state.playbackInfo.currentPositionMs + 10_000L).coerceAtMost(duration)) },
                    modifier = Modifier.weight(1f)
                )
                WalkmanMechanicalKey(
                    icon = Icons.Rounded.SkipNext,
                    label = "NEXT",
                    isPrimary = false,
                    accent = orangeAccent,
                    onClick = callbacks.onPlayNext,
                    modifier = Modifier.weight(1f).testTag("next_button")
                )
            }
        }
    }
}

@Composable
private fun WalkmanMechanicalKey(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isPrimary: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isPrimary) accent else Color(0xFF39404E),
        border = BorderStroke(1.dp, if (isPrimary) Color(0xFFFFA578) else Color(0xFF5D6678)),
        shadowElevation = 6.dp,
        modifier = modifier.height(64.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = label,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                color = Color.White.copy(alpha = 0.88f)
            )
        }
    }
}

@Composable
fun ArtworkCarouselPlayer(
    state: PlayerRenderState,
    callbacks: PlayerStyleCallbacks,
    gestureController: PlayerGestureController,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val palette = state.dynamicPalette
    val song = state.song
    val duration = state.playbackInfo.durationMs.coerceAtLeast(song?.durationMs ?: 0L)
    val accent = PlayerStyleManager.resolveAccentColor(state.appearance, colors, palette)

    val prevSong = state.queue.getOrNull(state.queueIndex - 1)
    val nextSong = state.queue.getOrNull(state.queueIndex + 1)

    Box(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .testTag("player_style_artwork_carousel"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                NowPlayingTopHeader(
                    song = song,
                    onClose = callbacks.onClose,
                    onOpenQueue = callbacks.onOpenQueue,
                    onOpenLyrics = callbacks.onOpenLyrics,
                    onOpenSleepTimer = callbacks.onOpenSleepTimer,
                    onOpenStyleWheel = callbacks.onArtworkLongPress,
                    onToggleVisualizer = callbacks.onToggleVisualizer,
                    isVisualizerActive = state.isVisualizerActive,
                    onOpenEqualizer = callbacks.onOpenEqualizer,
                    onOpenAudioStudio = callbacks.onOpenAudioEffects,
                    onOpenOutputDevice = callbacks.onOpenOutputDevice,
                    onToggleFocusMode = callbacks.onToggleFocusMode,
                    onOpenCustomization = callbacks.onOpenCustomization
                )
            }

            // 3-Card Swipeable Cover Flow Carousel (Prev • Active • Next)
            AdaptiveHeroSlot(
                widthFraction = 1f,
                aspectRatio = 1.52f
            ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Previous track peeking card
                Box(
                    modifier = Modifier
                        .weight(0.26f)
                        .aspectRatio(0.86f)
                        .graphicsLayer {
                            scaleX = 0.90f
                            scaleY = 0.86f
                            rotationY = 14f
                        }
                        .alpha(if (prevSong != null) 0.72f else 0.30f)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { callbacks.onPlayPrevious() }
                ) {
                    ArtworkAlbumView(
                        song = prevSong ?: song,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 18.dp,
                        isPlaying = false
                    )
                }

                // Active Hero Card with full swipe/double-tap gestures
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
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .shadow(18.dp, RoundedCornerShape(26.dp))
                )

                // Next track peeking card
                Box(
                    modifier = Modifier
                        .weight(0.26f)
                        .aspectRatio(0.86f)
                        .graphicsLayer {
                            scaleX = 0.90f
                            scaleY = 0.86f
                            rotationY = -14f
                        }
                        .alpha(if (nextSong != null) 0.72f else 0.30f)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { callbacks.onPlayNext() }
                ) {
                    ArtworkAlbumView(
                        song = nextSong ?: song,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 18.dp,
                        isPlaying = false
                    )
                }
            }
            }

            // Up Next quick indicator chip
            if (nextSong != null) {
                Surface(
                    onClick = callbacks.onOpenQueue,
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surfaceTinted,
                    border = BorderStroke(0.75.dp, colors.cardBorder)
                ) {
                    Text(
                        text = "Up Next: ${nextSong.title}",
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TrackMetadataBar(
                    song = song,
                    appearance = state.appearance,
                    onToggleFavorite = callbacks.onToggleFavorite,
                    onOpenLyrics = callbacks.onOpenLyrics,
                    onOpenQueue = callbacks.onOpenQueue,
                    accentColor = accent
                )

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

                PlaybackControls(
                    playbackInfo = state.playbackInfo,
                    onTogglePlayPause = callbacks.onTogglePlayPause,
                    onPlayNext = callbacks.onPlayNext,
                    onPlayPrevious = callbacks.onPlayPrevious,
                    onToggleShuffle = callbacks.onToggleShuffle,
                    onCycleRepeat = callbacks.onCycleRepeat,
                    appearance = state.appearance,
                    accentColor = accent,
                    primaryButtonSize = 64.dp
                )
            }
        }
    }
}
