package ui.player.styles

import android.os.Build
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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.example.ui.components.ArtworkAlbumView
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
 * Group A: Modern & Premium Now Playing Styles
 * - AMOLED Minimal
 * - Premium Gradient
 * - Liquid Glass
 * - Neumorphism
 * - Dynamic Color
 */

/**
 * Responsive centerpiece slot that claims remaining vertical space between header and controls,
 * sizing artwork to the largest rectangle that fits both the desired width fraction and available height.
 */
@Composable
internal fun ColumnScope.AdaptiveHeroSlot(
    widthFraction: Float = 0.84f,
    aspectRatio: Float = 1f,
    verticalPadding: Dp = 6.dp,
    content: @Composable BoxScope.() -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(vertical = verticalPadding),
        contentAlignment = Alignment.Center
    ) {
        val targetW = maxWidth * widthFraction.coerceIn(0.4f, 1f)
        val safeH = maxHeight.coerceAtLeast(64.dp)
        val maxWFromH = (safeH * aspectRatio).coerceAtLeast(64.dp)
        val resolvedW = minOf(targetW, maxWFromH).coerceAtLeast(64.dp)
        val resolvedH = (resolvedW / aspectRatio).coerceAtMost(safeH)
        Box(
            modifier = Modifier.size(width = resolvedW, height = resolvedH),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

@Composable
fun AmoledMinimalPlayer(
    state: PlayerRenderState,
    callbacks: PlayerStyleCallbacks,
    gestureController: PlayerGestureController,
    modifier: Modifier = Modifier
) {
    val song = state.song
    val duration = state.playbackInfo.durationMs.coerceAtLeast(song?.durationMs ?: 0L)
    val pureBlack = Color(0xFF000000)
    val crispWhite = Color(0xFFFFFFFF)
    val mutedWhite = Color(0xFF9EA2AB)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(pureBlack)
            .navigationBarsPadding()
            .testTag("player_style_amoled_minimal"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(horizontal = 24.dp, vertical = 8.dp),
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
                widthFraction = state.appearance.artworkSize.fraction.coerceIn(0.68f, 0.88f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = 0.75.dp,
                            color = crispWhite.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(PlayerStyleManager.resolveCornerRadius(state.appearance, 20.dp))
                        )
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
                        defaultCornerRadius = 18.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TrackMetadataBar(
                    song = song,
                    appearance = state.appearance,
                    onToggleFavorite = callbacks.onToggleFavorite,
                    onOpenLyrics = callbacks.onOpenLyrics,
                    onOpenQueue = callbacks.onOpenQueue,
                    titleColor = crispWhite,
                    subtitleColor = mutedWhite,
                    accentColor = crispWhite
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
                    accentColor = crispWhite,
                    inactiveColor = crispWhite.copy(alpha = 0.18f)
                )

                PlaybackControls(
                    playbackInfo = state.playbackInfo,
                    onTogglePlayPause = callbacks.onTogglePlayPause,
                    onPlayNext = callbacks.onPlayNext,
                    onPlayPrevious = callbacks.onPlayPrevious,
                    onToggleShuffle = callbacks.onToggleShuffle,
                    onCycleRepeat = callbacks.onCycleRepeat,
                    appearance = state.appearance,
                    accentColor = crispWhite,
                    onAccentColor = pureBlack,
                    iconTint = crispWhite,
                    mutedTint = mutedWhite,
                    primaryButtonSize = 68.dp
                )
            }
        }
    }
}

@Composable
fun PremiumGradientPlayer(
    state: PlayerRenderState,
    callbacks: PlayerStyleCallbacks,
    gestureController: PlayerGestureController,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val palette = state.dynamicPalette
    val song = state.song
    val duration = state.playbackInfo.durationMs.coerceAtLeast(song?.durationMs ?: 0L)
    val reduceAnim = LocalReduceAnimations.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val shouldAnimate = lifecycleState.isAtLeast(Lifecycle.State.STARTED) &&
        PlayerStyleManager.shouldRunAmbientAnimation(state, reduceAnim)
    val gradientStrength = state.appearance.gradientIntensity.coerceIn(0.15f, 1f)

    val phase = if (shouldAnimate) {
        val transition = rememberInfiniteTransition(label = "premiumGradientPhase")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 6.2831855f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 14000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "gradientAngle"
        )
        value
    } else 0.7f

    val deepBase = lerp(Color(0xFF0E1018), palette.background, 0.45f)
    val accent = PlayerStyleManager.resolveAccentColor(state.appearance, colors, palette)
    val textPrimary = Color(0xFFFDFDFF)
    val textMuted = Color(0xFFD6DAE6)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(deepBase)
            .navigationBarsPadding()
            .testTag("player_style_premium_gradient"),
        contentAlignment = Alignment.TopCenter
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val driftX = cos(phase) * size.width * 0.14f
            val driftY = sin(phase) * size.height * 0.10f
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        palette.primary.copy(alpha = 0.72f * gradientStrength),
                        palette.secondary.copy(alpha = 0.48f * gradientStrength),
                        deepBase
                    )
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.accent.copy(alpha = 0.42f * gradientStrength),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.25f + driftX, size.height * 0.24f + driftY),
                    radius = size.maxDimension * 0.58f
                ),
                radius = size.maxDimension * 0.58f,
                center = Offset(size.width * 0.25f + driftX, size.height * 0.24f + driftY)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.tertiary.copy(alpha = 0.34f * gradientStrength),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.80f - driftX, size.height * 0.65f - driftY),
                    radius = size.maxDimension * 0.52f
                ),
                radius = size.maxDimension * 0.52f,
                center = Offset(size.width * 0.80f - driftX, size.height * 0.65f - driftY)
            )
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
                widthFraction = state.appearance.artworkSize.fraction.coerceIn(0.76f, 0.94f)
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
                    defaultCornerRadius = 30.dp,
                    modifier = Modifier.fillMaxSize()
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
                    titleColor = textPrimary,
                    subtitleColor = textMuted,
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
                    accentColor = textPrimary,
                    inactiveColor = textPrimary.copy(alpha = 0.24f)
                )

                PlaybackControls(
                    playbackInfo = state.playbackInfo,
                    onTogglePlayPause = callbacks.onTogglePlayPause,
                    onPlayNext = callbacks.onPlayNext,
                    onPlayPrevious = callbacks.onPlayPrevious,
                    onToggleShuffle = callbacks.onToggleShuffle,
                    onCycleRepeat = callbacks.onCycleRepeat,
                    appearance = state.appearance,
                    accentColor = textPrimary,
                    onAccentColor = deepBase,
                    iconTint = textPrimary,
                    mutedTint = textMuted.copy(alpha = 0.72f),
                    primaryButtonSize = 72.dp
                )
            }
        }
    }
}

@Composable
fun LiquidGlassPlayer(
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
    val blurStrength = state.appearance.blurIntensity.coerceIn(0f, 1f)
    val specularBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.45f),
            Color.White.copy(alpha = 0.08f),
            palette.accent.copy(alpha = 0.22f)
        )
    )
    val inkPrimary = Color(0xFFF9FAFE)
    val inkMuted = Color(0xFFD2D8E6)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0D16))
            .navigationBarsPadding()
            .testTag("player_style_liquid_glass"),
        contentAlignment = Alignment.TopCenter
    ) {
        ArtworkAlbumView(
            song = song,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurStrength > 0.05f) {
                        Modifier.blur((16f + blurStrength * 28f).dp)
                    } else {
                        Modifier
                    }
                )
                .alpha(0.46f),
            cornerRadius = 0.dp,
            isPlaying = false
        )

        // Liquid specular caustic highlights
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0B1020).copy(alpha = 0.56f),
                        palette.primary.copy(alpha = 0.25f),
                        Color(0xFF080A12).copy(alpha = 0.84f)
                    )
                )
            )
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(size.width * 0.2f, size.height * 0.18f),
                    radius = size.width * 0.55f
                ),
                topLeft = Offset(-size.width * 0.1f, size.height * 0.02f),
                size = Size(size.width * 0.8f, size.height * 0.34f)
            )
        }

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

            // Double-layered liquid glass lens frame
            AdaptiveHeroSlot(
                widthFraction = state.appearance.artworkSize.fraction.coerceIn(0.74f, 0.90f)
            ) {
                Surface(
                    shape = RoundedCornerShape(36.dp),
                    color = Color.White.copy(alpha = 0.10f),
                    border = BorderStroke(1.3.dp, specularBrush),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .padding(6.dp)
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
                            defaultCornerRadius = 24.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Layered translucent deck with specular rim
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = Color.White.copy(alpha = 0.12f),
                border = BorderStroke(1.1.dp, specularBrush),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TrackMetadataBar(
                        song = song,
                        appearance = state.appearance,
                        onToggleFavorite = callbacks.onToggleFavorite,
                        onOpenLyrics = callbacks.onOpenLyrics,
                        onOpenQueue = callbacks.onOpenQueue,
                        titleColor = inkPrimary,
                        subtitleColor = inkMuted,
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
                        accentColor = inkPrimary,
                        inactiveColor = Color.White.copy(alpha = 0.24f)
                    )

                    PlaybackControls(
                        playbackInfo = state.playbackInfo,
                        onTogglePlayPause = callbacks.onTogglePlayPause,
                        onPlayNext = callbacks.onPlayNext,
                        onPlayPrevious = callbacks.onPlayPrevious,
                        onToggleShuffle = callbacks.onToggleShuffle,
                        onCycleRepeat = callbacks.onCycleRepeat,
                        appearance = state.appearance,
                        accentColor = Color.White.copy(alpha = 0.92f),
                        onAccentColor = Color(0xFF0E1320),
                        iconTint = inkPrimary,
                        mutedTint = inkMuted.copy(alpha = 0.72f),
                        primaryButtonSize = 66.dp
                    )
                }
            }
        }
    }
}

@Composable
fun NeumorphismPlayer(
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

    val neoBase = if (colors.isDark) Color(0xFF1F232D) else Color(0xFFE9EDF5)
    val lightShadow = if (colors.isDark) Color(0xFF2B303E) else Color(0xFFFFFFFF)
    val darkShadow = if (colors.isDark) Color(0xFF14171F) else Color(0xFFC5CDDC)
    val textPrimary = if (colors.isDark) Color(0xFFF2F5FA) else Color(0xFF1D2433)
    val textSecondary = if (colors.isDark) Color(0xFFA0A9BC) else Color(0xFF5D677A)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(neoBase)
            .navigationBarsPadding()
            .testTag("player_style_neumorphism"),
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

            // Soft raised extruded artwork platter
            AdaptiveHeroSlot(
                widthFraction = state.appearance.artworkSize.fraction.coerceIn(0.72f, 0.88f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val cr = CornerRadius(34.dp.toPx(), 34.dp.toPx())
                            val offset = 8.dp.toPx()
                            drawRoundRect(
                                color = darkShadow.copy(alpha = 0.85f),
                                topLeft = Offset(offset, offset),
                                size = size,
                                cornerRadius = cr
                            )
                            drawRoundRect(
                                color = lightShadow.copy(alpha = 0.90f),
                                topLeft = Offset(-offset * 0.85f, -offset * 0.85f),
                                size = size,
                                cornerRadius = cr
                            )
                        }
                        .clip(RoundedCornerShape(34.dp))
                        .background(neoBase)
                        .padding(14.dp)
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
                        defaultCornerRadius = 24.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                TrackMetadataBar(
                    song = song,
                    appearance = state.appearance,
                    onToggleFavorite = callbacks.onToggleFavorite,
                    onOpenLyrics = callbacks.onOpenLyrics,
                    onOpenQueue = callbacks.onOpenQueue,
                    titleColor = textPrimary,
                    subtitleColor = textSecondary,
                    accentColor = accent
                )

                // Recessed inset seekbar well
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(darkShadow.copy(alpha = 0.35f), lightShadow.copy(alpha = 0.40f))
                            )
                        )
                        .border(1.dp, lightShadow.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
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
                        accentColor = accent,
                        inactiveColor = darkShadow
                    )
                }

                // Soft raised control dock
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            val cr = CornerRadius(28.dp.toPx(), 28.dp.toPx())
                            drawRoundRect(
                                color = darkShadow.copy(alpha = 0.72f),
                                topLeft = Offset(5.dp.toPx(), 6.dp.toPx()),
                                size = size,
                                cornerRadius = cr
                            )
                            drawRoundRect(
                                color = lightShadow.copy(alpha = 0.85f),
                                topLeft = Offset(-4.dp.toPx(), -5.dp.toPx()),
                                size = size,
                                cornerRadius = cr
                            )
                        }
                        .clip(RoundedCornerShape(28.dp))
                        .background(neoBase)
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
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
                        iconTint = textPrimary,
                        mutedTint = textSecondary,
                        primaryButtonSize = 66.dp
                    )
                }
            }
        }
    }
}

@Composable
fun DynamicColorPlayer(
    state: PlayerRenderState,
    callbacks: PlayerStyleCallbacks,
    gestureController: PlayerGestureController,
    modifier: Modifier = Modifier
) {
    val palette = state.dynamicPalette
    val song = state.song
    val duration = state.playbackInfo.durationMs.coerceAtLeast(song?.durationMs ?: 0L)
    val strength = state.appearance.dynamicColorIntensity.coerceIn(0.25f, 1f)

    val canvasColor = lerp(Color(0xFF14151D), palette.primary, 0.55f * strength)
    val cardColor = lerp(Color(0xFF1C1F2B), palette.secondary, 0.52f * strength)
    val accentPop = lerp(Color(0xFFFFFFFF), palette.accent, 0.65f)
    val inkPrimary = Color(0xFFFFFFFF)
    val inkSecondary = Color(0xFFE3E7F2)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        lerp(canvasColor, palette.primary, 0.45f),
                        canvasColor,
                        lerp(Color(0xFF0C0E14), palette.background, 0.6f)
                    )
                )
            )
            .navigationBarsPadding()
            .testTag("player_style_dynamic_color"),
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

            // Chromatic color-matched mat framing the album cover
            AdaptiveHeroSlot(
                widthFraction = state.appearance.artworkSize.fraction.coerceIn(0.76f, 0.92f)
            ) {
                Surface(
                    shape = RoundedCornerShape(34.dp),
                    color = cardColor.copy(alpha = 0.85f),
                    border = BorderStroke(1.5.dp, accentPop.copy(alpha = 0.42f)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(14.dp)) {
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

            // Palette swatch pills showing live extracted album colors
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(palette.primary, palette.secondary, palette.accent, palette.tertiary).forEach { swatch ->
                    Box(
                        modifier = Modifier
                            .size(width = 24.dp, height = 6.dp)
                            .clip(CircleShape)
                            .background(swatch)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(28.dp),
                color = cardColor.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, inkPrimary.copy(alpha = 0.16f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TrackMetadataBar(
                        song = song,
                        appearance = state.appearance,
                        onToggleFavorite = callbacks.onToggleFavorite,
                        onOpenLyrics = callbacks.onOpenLyrics,
                        onOpenQueue = callbacks.onOpenQueue,
                        titleColor = inkPrimary,
                        subtitleColor = inkSecondary,
                        accentColor = accentPop
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
                        accentColor = accentPop,
                        inactiveColor = inkPrimary.copy(alpha = 0.22f)
                    )

                    PlaybackControls(
                        playbackInfo = state.playbackInfo,
                        onTogglePlayPause = callbacks.onTogglePlayPause,
                        onPlayNext = callbacks.onPlayNext,
                        onPlayPrevious = callbacks.onPlayPrevious,
                        onToggleShuffle = callbacks.onToggleShuffle,
                        onCycleRepeat = callbacks.onCycleRepeat,
                        appearance = state.appearance,
                        accentColor = accentPop,
                        onAccentColor = Color(0xFF10131C),
                        iconTint = inkPrimary,
                        mutedTint = inkSecondary.copy(alpha = 0.72f),
                        primaryButtonSize = 68.dp
                    )
                }
            }
        }
    }
}
