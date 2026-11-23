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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Group C: Immersive & Animated Now Playing Styles
 * All continuous visual loops pause automatically when playback pauses,
 * when AnimationIntensity == OFF, when Reduce Animations is enabled,
 * or when the lifecycle is not STARTED.
 *
 * - Audio Visualizer
 * - Aurora
 * - Liquid Music
 * - Particle Flow
 * - 3D Spatial Player
 * - Cinematic
 */

@Composable
private fun rememberLifecycleAwarePhase(
    state: PlayerRenderState,
    durationMs: Int,
    label: String
): Float {
    val reduceAnim = LocalReduceAnimations.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val shouldRun = lifecycleState.isAtLeast(Lifecycle.State.STARTED) &&
        PlayerStyleManager.shouldRunAmbientAnimation(state, reduceAnim)
    if (!shouldRun) return 0.25f

    val speedScale = state.appearance.animationIntensity.multiplier.coerceAtLeast(0.4f)
    val adjustedDuration = (durationMs / speedScale).toInt().coerceIn(2400, 22000)
    val transition = rememberInfiniteTransition(label = label)
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = adjustedDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "${label}_phase"
    )
    return phase
}

private fun computeBassEnergy(frequencies: FloatArray, isPlaying: Boolean): Float {
    if (!isPlaying) return 0.08f
    if (frequencies.isEmpty()) return 0.36f
    val sampleCount = frequencies.size.coerceAtMost(8)
    var sum = 0f
    for (i in 0 until sampleCount) sum += frequencies[i].coerceIn(0f, 1f)
    return (sum / sampleCount).coerceIn(0.12f, 1f)
}

@Composable
fun AudioVisualizerPlayer(
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
    val phase = rememberLifecycleAwarePhase(state, durationMs = 7000, label = "audioVisualizer")
    val isLive = state.isNowPlayingActive && state.playbackInfo.isPlaying
    val bass = computeBassEnergy(state.frequencies, isLive)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A0D16), Color(0xFF101524), Color(0xFF080A12))
                )
            )
            .navigationBarsPadding()
            .testTag("player_style_audio_visualizer"),
        contentAlignment = Alignment.TopCenter
    ) {
        // Rising audio-energy particles behind player
        Canvas(modifier = Modifier.fillMaxSize()) {
            val particleCount = 26
            for (i in 0 until particleCount) {
                val seed = (i * 0.6180339f) % 1f
                val band = if (state.frequencies.isNotEmpty()) {
                    state.frequencies[i % state.frequencies.size].coerceIn(0.05f, 1f)
                } else bass
                val x = size.width * ((seed + sin((phase * 2 * PI + i).toDouble()).toFloat() * 0.04f).coerceIn(0.03f, 0.97f))
                val progressY = (1f - ((phase + seed) % 1f))
                val y = size.height * (0.14f + progressY * 0.72f)
                val radius = (2.2f + band * 5.2f).dp.toPx()
                val alpha = ((if (isLive) 0.55f else 0.16f) * (1f - progressY * 0.5f)).coerceIn(0.05f, 0.75f)
                drawCircle(
                    color = if (i % 2 == 0) accent.copy(alpha = alpha) else palette.accent.copy(alpha = alpha),
                    radius = radius,
                    center = Offset(x, y)
                )
            }
        }

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

            // Radial circular visualizer halo + centered circular album artwork
            AdaptiveHeroSlot(
                widthFraction = 0.84f
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val innerR = size.minDimension * 0.34f
                    val maxBarLen = size.minDimension * 0.14f
                    val bars = 48
                    for (i in 0 until bars) {
                        val angleRad = (i.toDouble() / bars) * 2.0 * PI + phase * 2.0 * PI * 0.2
                        val freqMag = if (isLive && state.frequencies.isNotEmpty()) {
                            state.frequencies[i % state.frequencies.size].coerceIn(0.08f, 1f)
                        } else if (isLive) {
                            (0.25f + 0.55f * ((sin(i * 0.7 + phase * 12.0).toFloat() + 1f) / 2f))
                        } else 0.10f
                        val barLen = maxBarLen * freqMag
                        val start = Offset(
                            center.x + (cos(angleRad) * innerR).toFloat(),
                            center.y + (sin(angleRad) * innerR).toFloat()
                        )
                        val end = Offset(
                            center.x + (cos(angleRad) * (innerR + barLen)).toFloat(),
                            center.y + (sin(angleRad) * (innerR + barLen)).toFloat()
                        )
                        drawLine(
                            brush = Brush.linearGradient(listOf(accent, palette.accent), start = start, end = end),
                            start = start,
                            end = end,
                            strokeWidth = 3.2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
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
                    isCircular = true,
                    modifier = Modifier.fillMaxSize(0.62f)
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
                    titleColor = Color.White,
                    subtitleColor = Color(0xFFBDC5D6),
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
                    accentColor = accent,
                    inactiveColor = Color.White.copy(alpha = 0.22f)
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
                    onAccentColor = Color.White,
                    iconTint = Color.White,
                    mutedTint = Color(0xFF9EA6B8),
                    primaryButtonSize = 68.dp
                )
            }
        }
    }
}

@Composable
fun AuroraPlayer(
    state: PlayerRenderState,
    callbacks: PlayerStyleCallbacks,
    gestureController: PlayerGestureController,
    modifier: Modifier = Modifier
) {
    val palette = state.dynamicPalette
    val song = state.song
    val duration = state.playbackInfo.durationMs.coerceAtLeast(song?.durationMs ?: 0L)
    val phase = rememberLifecycleAwarePhase(state, durationMs = 11000, label = "auroraRibbons")
    val gradientStrength = state.appearance.gradientIntensity.coerceIn(0.25f, 1f)
    val auroraTeal = Color(0xFF2EE6C5)
    val auroraViolet = Color(0xFF8B6BFF)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF060913))
            .navigationBarsPadding()
            .testTag("player_style_aurora"),
        contentAlignment = Alignment.TopCenter
    ) {
        // Flowing northern lights aurora curtains
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ribbons = listOf(
                Triple(palette.primary, 0.24f, 0f),
                Triple(auroraTeal, 0.38f, 1.9f),
                Triple(auroraViolet, 0.52f, 3.6f)
            )
            ribbons.forEachIndexed { idx, (tint, baseY, offset) ->
                val path = Path()
                val steps = 20
                val yAnchor = size.height * baseY
                path.moveTo(0f, yAnchor)
                for (s in 0..steps) {
                    val t = s.toFloat() / steps
                    val x = size.width * t
                    val wave = sin((t * 4.5 + phase * 2 * PI + offset)).toFloat() * size.height * 0.08f
                    val y = yAnchor + wave
                    path.lineTo(x, y)
                }
                path.lineTo(size.width, yAnchor + size.height * 0.24f)
                path.lineTo(0f, yAnchor + size.height * 0.24f)
                path.close()
                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            tint.copy(alpha = (0.44f - idx * 0.06f) * gradientStrength),
                            palette.accent.copy(alpha = 0.14f * gradientStrength),
                            Color.Transparent
                        ),
                        startY = yAnchor - size.height * 0.1f,
                        endY = yAnchor + size.height * 0.28f
                    )
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(horizontal = 22.dp, vertical = 6.dp),
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
                widthFraction = state.appearance.artworkSize.fraction.coerceIn(0.74f, 0.90f)
            ) {
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = Color.White.copy(alpha = 0.07f),
                    border = BorderStroke(1.dp, auroraTeal.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
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
                            modifier = Modifier.fillMaxSize()
                        )
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
                    titleColor = Color.White,
                    subtitleColor = Color(0xFFC5D5E8),
                    accentColor = auroraTeal
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
                    accentColor = auroraTeal,
                    inactiveColor = Color.White.copy(alpha = 0.22f)
                )

                PlaybackControls(
                    playbackInfo = state.playbackInfo,
                    onTogglePlayPause = callbacks.onTogglePlayPause,
                    onPlayNext = callbacks.onPlayNext,
                    onPlayPrevious = callbacks.onPlayPrevious,
                    onToggleShuffle = callbacks.onToggleShuffle,
                    onCycleRepeat = callbacks.onCycleRepeat,
                    appearance = state.appearance,
                    accentColor = auroraTeal,
                    onAccentColor = Color(0xFF061518),
                    iconTint = Color.White,
                    mutedTint = Color(0xFF9FB2C9),
                    primaryButtonSize = 68.dp
                )
            }
        }
    }
}

@Composable
fun LiquidMusicPlayer(
    state: PlayerRenderState,
    callbacks: PlayerStyleCallbacks,
    gestureController: PlayerGestureController,
    modifier: Modifier = Modifier
) {
    val palette = state.dynamicPalette
    val song = state.song
    val duration = state.playbackInfo.durationMs.coerceAtLeast(song?.durationMs ?: 0L)
    val phase = rememberLifecycleAwarePhase(state, durationMs = 9000, label = "liquidMusic")
    val bass = computeBassEnergy(state.frequencies, state.isNowPlayingActive && state.playbackInfo.isPlaying)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C0E18))
            .navigationBarsPadding()
            .testTag("player_style_liquid_music"),
        contentAlignment = Alignment.TopCenter
    ) {
        // Organic morphing liquid metaball shapes
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width * 0.5f
            val cy = size.height * 0.40f
            val blobs = listOf(
                Triple(palette.primary, 0f, 0.36f),
                Triple(palette.secondary, 2.1f, 0.31f),
                Triple(palette.accent, 4.2f, 0.27f)
            )
            blobs.forEach { (blobColor, angleOffset, sizeRatio) ->
                val orbit = size.width * (0.12f + bass * 0.08f)
                val angle = phase * 2.0 * PI + angleOffset
                val bx = cx + (cos(angle) * orbit).toFloat()
                val by = cy + (sin(angle * 1.3) * orbit * 0.85f).toFloat()
                val r = size.minDimension * sizeRatio * (0.95f + bass * 0.20f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(blobColor.copy(alpha = 0.65f), blobColor.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(bx, by),
                        radius = r
                    ),
                    radius = r,
                    center = Offset(bx, by)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(horizontal = 22.dp, vertical = 6.dp),
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
                widthFraction = 0.76f
            ) {
                // Morphing organic liquid ring around circular album art
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val baseR = size.minDimension * 0.42f
                    val path = Path()
                    val pts = 48
                    for (i in 0..pts) {
                        val theta = (i.toDouble() / pts) * 2.0 * PI
                        val wobble = (sin(theta * 3 + phase * 2 * PI) * 8f + cos(theta * 5 - phase * 4 * PI) * 5f).toFloat()
                        val r = baseR + wobble * (0.6f + bass)
                        val x = center.x + (cos(theta) * r).toFloat()
                        val y = center.y + (sin(theta) * r).toFloat()
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    drawPath(
                        path = path,
                        brush = Brush.sweepGradient(
                            colors = listOf(palette.primary, palette.accent, palette.secondary, palette.primary),
                            center = center
                        ),
                        style = Stroke(width = 4.dp.toPx())
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
                    isCircular = true,
                    modifier = Modifier.fillMaxSize(0.76f)
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
                    titleColor = Color.White,
                    subtitleColor = Color(0xFFD2D8E8),
                    accentColor = palette.accent
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
                    accentColor = palette.accent,
                    inactiveColor = Color.White.copy(alpha = 0.20f)
                )

                PlaybackControls(
                    playbackInfo = state.playbackInfo,
                    onTogglePlayPause = callbacks.onTogglePlayPause,
                    onPlayNext = callbacks.onPlayNext,
                    onPlayPrevious = callbacks.onPlayPrevious,
                    onToggleShuffle = callbacks.onToggleShuffle,
                    onCycleRepeat = callbacks.onCycleRepeat,
                    appearance = state.appearance,
                    accentColor = palette.accent,
                    onAccentColor = Color(0xFF0A0D16),
                    iconTint = Color.White,
                    mutedTint = Color(0xFFA2ABC2),
                    primaryButtonSize = 68.dp
                )
            }
        }
    }
}

@Composable
fun ParticleFlowPlayer(
    state: PlayerRenderState,
    callbacks: PlayerStyleCallbacks,
    gestureController: PlayerGestureController,
    modifier: Modifier = Modifier
) {
    val palette = state.dynamicPalette
    val song = state.song
    val duration = state.playbackInfo.durationMs.coerceAtLeast(song?.durationMs ?: 0L)
    val phase = rememberLifecycleAwarePhase(state, durationMs = 8000, label = "particleFlow")
    val isLive = state.isNowPlayingActive && state.playbackInfo.isPlaying
    val bass = computeBassEnergy(state.frequencies, isLive)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF070810), Color(0xFF121426), Color(0xFF070810)))
            )
            .navigationBarsPadding()
            .testTag("player_style_particle_flow"),
        contentAlignment = Alignment.TopCenter
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val streamCount = 42
            for (i in 0 until streamCount) {
                val lane = (i % 7) / 7f
                val t = (phase + (i * 0.137f)) % 1f
                val x = size.width * t
                val waveY = sin((t * 6.0 + i * 0.5)).toFloat() * size.height * (0.04f + bass * 0.04f)
                val y = size.height * (0.14f + lane * 0.62f) + waveY
                val rad = (1.8f + ((i % 4) * 0.9f) + bass * 2.4f).dp.toPx()
                val alpha = (sin(t * PI).toFloat() * (if (isLive) 0.78f else 0.25f)).coerceIn(0.05f, 0.85f)
                val dotColor = when (i % 3) {
                    0 -> palette.primary
                    1 -> palette.accent
                    else -> Color.White
                }
                drawCircle(color = dotColor.copy(alpha = alpha), radius = rad, center = Offset(x, y))
                // Short glowing motion tail
                drawLine(
                    color = dotColor.copy(alpha = alpha * 0.45f),
                    start = Offset((x - 14.dp.toPx()).coerceAtLeast(0f), y),
                    end = Offset(x, y),
                    strokeWidth = rad * 0.75f,
                    cap = StrokeCap.Round
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(horizontal = 22.dp, vertical = 6.dp),
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
                widthFraction = state.appearance.artworkSize.fraction.coerceIn(0.72f, 0.88f)
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
                    defaultCornerRadius = 28.dp,
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, palette.accent.copy(alpha = 0.45f), RoundedCornerShape(28.dp))
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
                    titleColor = Color.White,
                    subtitleColor = Color(0xFFC5CCE0),
                    accentColor = palette.accent
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
                    accentColor = palette.accent,
                    inactiveColor = Color.White.copy(alpha = 0.22f)
                )

                PlaybackControls(
                    playbackInfo = state.playbackInfo,
                    onTogglePlayPause = callbacks.onTogglePlayPause,
                    onPlayNext = callbacks.onPlayNext,
                    onPlayPrevious = callbacks.onPlayPrevious,
                    onToggleShuffle = callbacks.onToggleShuffle,
                    onCycleRepeat = callbacks.onCycleRepeat,
                    appearance = state.appearance,
                    accentColor = palette.primary,
                    onAccentColor = Color.White,
                    iconTint = Color.White,
                    mutedTint = Color(0xFF9AA3BD),
                    primaryButtonSize = 68.dp
                )
            }
        }
    }
}

@Composable
fun Spatial3DPlayer(
    state: PlayerRenderState,
    callbacks: PlayerStyleCallbacks,
    gestureController: PlayerGestureController,
    modifier: Modifier = Modifier
) {
    val palette = state.dynamicPalette
    val song = state.song
    val duration = state.playbackInfo.durationMs.coerceAtLeast(song?.durationMs ?: 0L)
    val phase = rememberLifecycleAwarePhase(state, durationMs = 10000, label = "spatial3d")
    val isLive = state.isNowPlayingActive && state.playbackInfo.isPlaying
    val bass = computeBassEnergy(state.frequencies, isLive)

    val tiltY = if (isLive) sin(phase * 2.0 * PI).toFloat() * 14f else -8f
    val tiltX = if (isLive) cos(phase * 2.0 * PI).toFloat() * 7f else 5f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        palette.primary.copy(alpha = 0.34f),
                        Color(0xFF0C101C),
                        Color(0xFF06070D)
                    )
                )
            )
            .navigationBarsPadding()
            .testTag("player_style_spatial_3d"),
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

            // 3D Spatial Soundstage + Perspective Tilted Artwork
            AdaptiveHeroSlot(
                widthFraction = 0.88f
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height * 0.56f
                    for (ring in 1..4) {
                        val rx = size.width * (0.24f + ring * 0.085f + bass * 0.025f)
                        val ry = rx * 0.34f
                        drawOval(
                            color = palette.accent.copy(alpha = (0.45f - ring * 0.08f).coerceAtLeast(0.10f)),
                            topLeft = Offset(cx - rx, cy - ry + 26.dp.toPx()),
                            size = Size(rx * 2f, ry * 2f),
                            style = Stroke(width = 1.6.dp.toPx())
                        )
                    }
                    // Orbiting spatial sound node
                    val nodeAngle = phase * 2.0 * PI
                    val orbitRx = size.width * 0.44f
                    val orbitRy = orbitRx * 0.34f
                    val nodePos = Offset(
                        cx + (cos(nodeAngle) * orbitRx).toFloat(),
                        cy + 26.dp.toPx() + (sin(nodeAngle) * orbitRy).toFloat()
                    )
                    drawCircle(color = Color.White, radius = 5.dp.toPx(), center = nodePos)
                    drawCircle(color = palette.accent.copy(alpha = 0.45f), radius = 12.dp.toPx(), center = nodePos)
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
                        .fillMaxSize(0.72f)
                        .graphicsLayer {
                            rotationY = tiltY
                            rotationX = tiltX
                            cameraDistance = 14f * density
                        }
                        .shadow(22.dp, RoundedCornerShape(26.dp))
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(0.75.dp, palette.accent.copy(alpha = 0.4f))
            ) {
                Text(
                    text = "SPATIAL 3D STAGE • HEAD-TRACKED DEPTH",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp,
                    color = palette.accent,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
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
                    titleColor = Color.White,
                    subtitleColor = Color(0xFFC2CAE0),
                    accentColor = palette.accent
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
                    accentColor = palette.accent,
                    inactiveColor = Color.White.copy(alpha = 0.22f)
                )

                PlaybackControls(
                    playbackInfo = state.playbackInfo,
                    onTogglePlayPause = callbacks.onTogglePlayPause,
                    onPlayNext = callbacks.onPlayNext,
                    onPlayPrevious = callbacks.onPlayPrevious,
                    onToggleShuffle = callbacks.onToggleShuffle,
                    onCycleRepeat = callbacks.onCycleRepeat,
                    appearance = state.appearance,
                    accentColor = Color.White,
                    onAccentColor = Color(0xFF0A0D16),
                    iconTint = Color.White,
                    mutedTint = Color(0xFF9EA7BF),
                    primaryButtonSize = 68.dp
                )
            }
        }
    }
}

@Composable
fun CinematicPlayer(
    state: PlayerRenderState,
    callbacks: PlayerStyleCallbacks,
    gestureController: PlayerGestureController,
    modifier: Modifier = Modifier
) {
    val palette = state.dynamicPalette
    val song = state.song
    val duration = state.playbackInfo.durationMs.coerceAtLeast(song?.durationMs ?: 0L)
    val goldAccent = Color(0xFFE6C27A)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF050507))
            .navigationBarsPadding()
            .testTag("player_style_cinematic"),
        contentAlignment = Alignment.TopCenter
    ) {
        // Overhead theater spotlight cone
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cone = Path().apply {
                moveTo(size.width * 0.34f, 0f)
                lineTo(size.width * 0.66f, 0f)
                lineTo(size.width * 0.96f, size.height * 0.72f)
                lineTo(size.width * 0.04f, size.height * 0.72f)
                close()
            }
            drawPath(
                path = cone,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        palette.primary.copy(alpha = 0.28f),
                        goldAccent.copy(alpha = 0.08f),
                        Color.Transparent
                    )
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 620.dp)
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

            // Widescreen Letterbox Cinema Frame
            AdaptiveHeroSlot(
                widthFraction = 1f,
                aspectRatio = 1.42f
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(Color.Black)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF101014))
                            .border(0.75.dp, goldAccent.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
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
                            defaultCornerRadius = 8.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Subtle cinematic letterbox vignette
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.35f),
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.52f)
                                        )
                                    )
                                )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(Color.Black)
                    )
                }
            }

            // Cinema Subtitle Style Title & Artist
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "NOW SCREENING",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    letterSpacing = 2.6.sp,
                    color = goldAccent.copy(alpha = 0.85f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = song?.title ?: "Untitled Feature",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = Color(0xFFFDFBF6),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song?.artist ?: "SONORA Soundstage",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 13.sp,
                    letterSpacing = 0.8.sp,
                    color = Color(0xFFBDB6A8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AdaptiveSeekbar(
                    currentPositionMs = state.playbackInfo.currentPositionMs,
                    durationMs = duration,
                    onSeekTo = callbacks.onSeekTo,
                    isPlaying = state.playbackInfo.isPlaying,
                    style = state.seekbarStyle,
                    appearance = state.appearance,
                    liveWaveform = state.waveformSamples,
                    hapticFeedbackEnabled = state.gestureConfig.hapticFeedbackEnabled,
                    accentColor = goldAccent,
                    inactiveColor = Color.White.copy(alpha = 0.18f)
                )

                PlaybackControls(
                    playbackInfo = state.playbackInfo,
                    onTogglePlayPause = callbacks.onTogglePlayPause,
                    onPlayNext = callbacks.onPlayNext,
                    onPlayPrevious = callbacks.onPlayPrevious,
                    onToggleShuffle = callbacks.onToggleShuffle,
                    onCycleRepeat = callbacks.onCycleRepeat,
                    appearance = state.appearance,
                    accentColor = goldAccent,
                    onAccentColor = Color(0xFF14110B),
                    iconTint = Color(0xFFF5F1E8),
                    mutedTint = Color(0xFF8E887B),
                    primaryButtonSize = 66.dp
                )
            }
        }
    }
}
