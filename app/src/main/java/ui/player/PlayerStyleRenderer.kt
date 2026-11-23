package ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.model.PlayerLayoutOption
import com.example.model.PlayerStyle
import ui.player.styles.AmoledMinimalPlayer
import ui.player.styles.ArtworkCarouselPlayer
import ui.player.styles.AudioVisualizerPlayer
import ui.player.styles.AuroraPlayer
import ui.player.styles.CassettePlayer
import ui.player.styles.CdPlayer
import ui.player.styles.CinematicPlayer
import ui.player.styles.DynamicColorPlayer
import ui.player.styles.FloatingArtworkPlayer
import ui.player.styles.GlassPlayer
import ui.player.styles.LandscapePlayer
import ui.player.styles.LiquidGlassPlayer
import ui.player.styles.LiquidMusicPlayer
import ui.player.styles.LyricsFocusPlayer
import ui.player.styles.MaterialYouPlayer
import ui.player.styles.MinimalPlayer
import ui.player.styles.ModernCompactPlayer
import ui.player.styles.NeonPlayer
import ui.player.styles.NeumorphismPlayer
import ui.player.styles.OrbitPlayer
import ui.player.styles.ParticleFlowPlayer
import ui.player.styles.PolaroidPlayer
import ui.player.styles.PosterPlayer
import ui.player.styles.PremiumGradientPlayer
import ui.player.styles.RetroWalkmanPlayer
import ui.player.styles.Spatial3DPlayer
import ui.player.styles.SpectrumPlayer
import ui.player.styles.SplitLayoutPlayer
import ui.player.styles.TypographyPlayer
import ui.player.styles.VinylPlayer
import ui.player.styles.WaveformPlayer

/**
 * Unified Now Playing Style Renderer dispatching all 40 PlayerStyle options
 * (30 curated modern/artwork/immersive/compact styles + 10 preserved legacy SONORA styles)
 * and honoring PlayerLayoutOption overrides when requested.
 */
@Composable
fun PlayerStyleRenderer(
    style: PlayerStyle,
    state: PlayerRenderState,
    callbacks: PlayerStyleCallbacks,
    gestureController: PlayerGestureController,
    modifier: Modifier = Modifier,
    respectLayoutOverride: Boolean = true
) {
    val effectiveStyle = if (respectLayoutOverride) {
        when (state.appearance.layoutOption) {
            PlayerLayoutOption.STYLE_DEFAULT -> style
            PlayerLayoutOption.ARTWORK_ABOVE -> style
            PlayerLayoutOption.ARTWORK_CENTERED -> if (style == PlayerStyle.CLASSIC) PlayerStyle.FLOATING_ARTWORK else style
            PlayerLayoutOption.ARTWORK_FULLSCREEN -> PlayerStyle.CANVAS
            PlayerLayoutOption.ARTWORK_LEFT_CONTROLS_RIGHT -> PlayerStyle.LANDSCAPE_PLAYER
            PlayerLayoutOption.LYRICS_FIRST -> PlayerStyle.LYRICS_FOCUS
            PlayerLayoutOption.COMPACT -> PlayerStyle.MODERN_COMPACT
        }
    } else {
        style
    }

    when (effectiveStyle) {
        // Group A: Modern & Premium
        PlayerStyle.GLASS -> GlassPlayer(state, callbacks, gestureController, modifier)
        PlayerStyle.AMOLED_MINIMAL -> AmoledMinimalPlayer(state, callbacks, gestureController, modifier)
        PlayerStyle.PREMIUM_GRADIENT -> PremiumGradientPlayer(state, callbacks, gestureController, modifier)
        PlayerStyle.MATERIAL_YOU -> MaterialYouPlayer(state, callbacks, gestureController, modifier)
        PlayerStyle.LIQUID_GLASS -> LiquidGlassPlayer(state, callbacks, gestureController, modifier)
        PlayerStyle.NEUMORPHISM -> NeumorphismPlayer(state, callbacks, gestureController, modifier)
        PlayerStyle.DYNAMIC_COLOR -> DynamicColorPlayer(state, callbacks, gestureController, modifier)
        PlayerStyle.EDITORIAL -> EditorialPlayer(
            song = state.song,
            playbackInfo = state.playbackInfo,
            onTogglePlayPause = callbacks.onTogglePlayPause,
            onPlayNext = callbacks.onPlayNext,
            onPlayPrevious = callbacks.onPlayPrevious,
            onSeekTo = callbacks.onSeekTo,
            onToggleShuffle = callbacks.onToggleShuffle,
            onCycleRepeat = callbacks.onCycleRepeat,
            onOpenQueue = callbacks.onOpenQueue,
            onOpenLyrics = callbacks.onOpenLyrics,
            onOpenSleepTimer = callbacks.onOpenSleepTimer,
            onClose = callbacks.onClose,
            onArtworkLongPress = callbacks.onArtworkLongPress,
            seekbarStyle = state.seekbarStyle,
            appearance = state.appearance,
            gestureConfig = state.gestureConfig,
            gestureController = gestureController,
            liveWaveform = state.waveformSamples,
            onToggleFavorite = callbacks.onToggleFavorite,
            onCyclePlayerStyle = callbacks.onCyclePlayerStyle,
            modifier = modifier
        )

        // Group B: Album Artwork Focused
        PlayerStyle.CANVAS -> CanvasPlayer(
            song = state.song,
            playbackInfo = state.playbackInfo,
            onTogglePlayPause = callbacks.onTogglePlayPause,
            onPlayNext = callbacks.onPlayNext,
            onPlayPrevious = callbacks.onPlayPrevious,
            onSeekTo = callbacks.onSeekTo,
            onToggleShuffle = callbacks.onToggleShuffle,
            onCycleRepeat = callbacks.onCycleRepeat,
            onOpenQueue = callbacks.onOpenQueue,
            onOpenLyrics = callbacks.onOpenLyrics,
            onOpenSleepTimer = callbacks.onOpenSleepTimer,
            onClose = callbacks.onClose,
            onArtworkLongPress = callbacks.onArtworkLongPress,
            seekbarStyle = state.seekbarStyle,
            appearance = state.appearance,
            gestureConfig = state.gestureConfig,
            gestureController = gestureController,
            liveWaveform = state.waveformSamples,
            onToggleFavorite = callbacks.onToggleFavorite,
            onCyclePlayerStyle = callbacks.onCyclePlayerStyle,
            modifier = modifier
        )
        PlayerStyle.FLOATING_ARTWORK -> FloatingArtworkPlayer(state, callbacks, gestureController, modifier)
        PlayerStyle.VINYL -> VinylPlayer(state, callbacks, gestureController, modifier)
        PlayerStyle.CASSETTE -> CassettePlayer(state, callbacks, gestureController, modifier)
        PlayerStyle.CD_PLAYER -> CdPlayer(state, callbacks, gestureController, modifier)
        PlayerStyle.RETRO_WALKMAN -> RetroWalkmanPlayer(state, callbacks, gestureController, modifier)
        PlayerStyle.POLAROID -> PolaroidPlayer(state, callbacks, gestureController, modifier)
        PlayerStyle.ARTWORK_CAROUSEL -> ArtworkCarouselPlayer(state, callbacks, gestureController, modifier)

        // Group C: Immersive & Animated
        PlayerStyle.WAVEFORM -> WaveformPlayer(state, callbacks, gestureController, modifier)
        PlayerStyle.SPECTRUM -> SpectrumPlayer(state, callbacks, gestureController, modifier)
        PlayerStyle.AUDIO_VISUALIZER -> AudioVisualizerPlayer(state, callbacks, gestureController, modifier)
        PlayerStyle.AURORA -> AuroraPlayer(state, callbacks, gestureController, modifier)
        PlayerStyle.LIQUID_MUSIC -> LiquidMusicPlayer(state, callbacks, gestureController, modifier)
        PlayerStyle.PARTICLE_FLOW -> ParticleFlowPlayer(state, callbacks, gestureController, modifier)
        PlayerStyle.SPATIAL_3D -> Spatial3DPlayer(state, callbacks, gestureController, modifier)
        PlayerStyle.CINEMATIC -> CinematicPlayer(state, callbacks, gestureController, modifier)

        // Group D: Compact & Functional
        PlayerStyle.CLASSIC -> ClassicPlayer(
            song = state.song,
            playbackInfo = state.playbackInfo,
            onTogglePlayPause = callbacks.onTogglePlayPause,
            onPlayNext = callbacks.onPlayNext,
            onPlayPrevious = callbacks.onPlayPrevious,
            onSeekTo = callbacks.onSeekTo,
            onToggleShuffle = callbacks.onToggleShuffle,
            onCycleRepeat = callbacks.onCycleRepeat,
            onOpenQueue = callbacks.onOpenQueue,
            onOpenLyrics = callbacks.onOpenLyrics,
            onOpenSleepTimer = callbacks.onOpenSleepTimer,
            onClose = callbacks.onClose,
            onArtworkLongPress = callbacks.onArtworkLongPress,
            seekbarStyle = state.seekbarStyle,
            onToggleFavorite = callbacks.onToggleFavorite,
            onOpenAudioEffects = callbacks.onOpenAudioEffects,
            onOpenEqualizer = callbacks.onOpenEqualizer,
            onOpenOutputDevice = callbacks.onOpenOutputDevice,
            onToggleFocusMode = callbacks.onToggleFocusMode,
            isVisualizerActive = state.isVisualizerActive,
            onToggleVisualizer = callbacks.onToggleVisualizer,
            frequencies = state.frequencies,
            waveform = state.waveformSamples,
            visualizerStyle = state.visualizerStyle,
            onCycleVisualizerStyle = callbacks.onCycleVisualizerStyle,
            appearance = state.appearance,
            gestureConfig = state.gestureConfig,
            gestureController = gestureController,
            onOpenCustomization = callbacks.onOpenCustomization,
            onCyclePlayerStyle = callbacks.onCyclePlayerStyle,
            modifier = modifier
        )
        PlayerStyle.MODERN_COMPACT -> ModernCompactPlayer(state, callbacks, gestureController, modifier)
        PlayerStyle.TYPOGRAPHY_PLAYER -> TypographyPlayer(state, callbacks, gestureController, modifier)
        PlayerStyle.SPLIT_LAYOUT -> SplitLayoutPlayer(state, callbacks, gestureController, modifier)
        PlayerStyle.LANDSCAPE_PLAYER -> LandscapePlayer(state, callbacks, gestureController, modifier)
        PlayerStyle.LYRICS_FOCUS -> LyricsFocusPlayer(state, callbacks, gestureController, modifier)

        // Preserved Legacy SONORA Styles
        PlayerStyle.MINIMAL -> MinimalPlayer(state, callbacks, gestureController, modifier)
        PlayerStyle.GESTURE -> GesturePlayer(
            song = state.song,
            playbackInfo = state.playbackInfo,
            onTogglePlayPause = callbacks.onTogglePlayPause,
            onPlayNext = callbacks.onPlayNext,
            onPlayPrevious = callbacks.onPlayPrevious,
            onSeekTo = callbacks.onSeekTo,
            onToggleShuffle = callbacks.onToggleShuffle,
            onCycleRepeat = callbacks.onCycleRepeat,
            onOpenQueue = callbacks.onOpenQueue,
            onOpenLyrics = callbacks.onOpenLyrics,
            onOpenSleepTimer = callbacks.onOpenSleepTimer,
            onClose = callbacks.onClose,
            onArtworkLongPress = callbacks.onArtworkLongPress,
            seekbarStyle = state.seekbarStyle,
            gesturesEnabled = state.gestureConfig.gesturesEnabled,
            swipeArtworkToSkip = state.gestureConfig.swipeArtworkEnabled,
            doubleTapToSeek = state.gestureConfig.doubleTapEnabled,
            swipeDownToCollapse = state.gestureConfig.swipeDownToMinimizeEnabled,
            appearance = state.appearance,
            gestureConfig = state.gestureConfig,
            gestureController = gestureController,
            liveWaveform = state.waveformSamples,
            onToggleFavorite = callbacks.onToggleFavorite,
            onCyclePlayerStyle = callbacks.onCyclePlayerStyle,
            modifier = modifier
        )
        PlayerStyle.BENTO -> BentoPlayer(
            song = state.song,
            playbackInfo = state.playbackInfo,
            onTogglePlayPause = callbacks.onTogglePlayPause,
            onPlayNext = callbacks.onPlayNext,
            onPlayPrevious = callbacks.onPlayPrevious,
            onSeekTo = callbacks.onSeekTo,
            onToggleShuffle = callbacks.onToggleShuffle,
            onCycleRepeat = callbacks.onCycleRepeat,
            onOpenQueue = callbacks.onOpenQueue,
            onOpenLyrics = callbacks.onOpenLyrics,
            onOpenSleepTimer = callbacks.onOpenSleepTimer,
            onClose = callbacks.onClose,
            onArtworkLongPress = callbacks.onArtworkLongPress,
            seekbarStyle = state.seekbarStyle,
            appearance = state.appearance,
            gestureConfig = state.gestureConfig,
            gestureController = gestureController,
            liveWaveform = state.waveformSamples,
            onToggleFavorite = callbacks.onToggleFavorite,
            onCyclePlayerStyle = callbacks.onCyclePlayerStyle,
            modifier = modifier
        )
        PlayerStyle.POSTER -> PosterPlayer(state, callbacks, gestureController, modifier)
        PlayerStyle.DIAL -> DialPlayer(
            song = state.song,
            playbackInfo = state.playbackInfo,
            onTogglePlayPause = callbacks.onTogglePlayPause,
            onPlayNext = callbacks.onPlayNext,
            onPlayPrevious = callbacks.onPlayPrevious,
            onSeekTo = callbacks.onSeekTo,
            onToggleShuffle = callbacks.onToggleShuffle,
            onCycleRepeat = callbacks.onCycleRepeat,
            onOpenQueue = callbacks.onOpenQueue,
            onOpenLyrics = callbacks.onOpenLyrics,
            onOpenSleepTimer = callbacks.onOpenSleepTimer,
            onClose = callbacks.onClose,
            onArtworkLongPress = callbacks.onArtworkLongPress,
            seekbarStyle = state.seekbarStyle,
            appearance = state.appearance,
            gestureConfig = state.gestureConfig,
            gestureController = gestureController,
            liveWaveform = state.waveformSamples,
            onToggleFavorite = callbacks.onToggleFavorite,
            onCyclePlayerStyle = callbacks.onCyclePlayerStyle,
            modifier = modifier
        )
        PlayerStyle.MORPH -> MorphPlayer(
            song = state.song,
            playbackInfo = state.playbackInfo,
            onTogglePlayPause = callbacks.onTogglePlayPause,
            onPlayNext = callbacks.onPlayNext,
            onPlayPrevious = callbacks.onPlayPrevious,
            onSeekTo = callbacks.onSeekTo,
            onToggleShuffle = callbacks.onToggleShuffle,
            onCycleRepeat = callbacks.onCycleRepeat,
            onOpenQueue = callbacks.onOpenQueue,
            onOpenLyrics = callbacks.onOpenLyrics,
            onOpenSleepTimer = callbacks.onOpenSleepTimer,
            onClose = callbacks.onClose,
            onArtworkLongPress = callbacks.onArtworkLongPress,
            seekbarStyle = state.seekbarStyle,
            appearance = state.appearance,
            gestureConfig = state.gestureConfig,
            gestureController = gestureController,
            liveWaveform = state.waveformSamples,
            onToggleFavorite = callbacks.onToggleFavorite,
            onCyclePlayerStyle = callbacks.onCyclePlayerStyle,
            modifier = modifier
        )
        PlayerStyle.STICKER -> StickerPlayer(
            song = state.song,
            playbackInfo = state.playbackInfo,
            onTogglePlayPause = callbacks.onTogglePlayPause,
            onPlayNext = callbacks.onPlayNext,
            onPlayPrevious = callbacks.onPlayPrevious,
            onSeekTo = callbacks.onSeekTo,
            onToggleShuffle = callbacks.onToggleShuffle,
            onCycleRepeat = callbacks.onCycleRepeat,
            onOpenQueue = callbacks.onOpenQueue,
            onOpenLyrics = callbacks.onOpenLyrics,
            onOpenSleepTimer = callbacks.onOpenSleepTimer,
            onClose = callbacks.onClose,
            onArtworkLongPress = callbacks.onArtworkLongPress,
            seekbarStyle = state.seekbarStyle,
            appearance = state.appearance,
            gestureConfig = state.gestureConfig,
            gestureController = gestureController,
            liveWaveform = state.waveformSamples,
            onToggleFavorite = callbacks.onToggleFavorite,
            onCyclePlayerStyle = callbacks.onCyclePlayerStyle,
            modifier = modifier
        )
        PlayerStyle.ORBIT -> OrbitPlayer(state, callbacks, gestureController, modifier)
        PlayerStyle.HERO -> HeroPlayer(
            song = state.song,
            playbackInfo = state.playbackInfo,
            onTogglePlayPause = callbacks.onTogglePlayPause,
            onPlayNext = callbacks.onPlayNext,
            onPlayPrevious = callbacks.onPlayPrevious,
            onSeekTo = callbacks.onSeekTo,
            onToggleShuffle = callbacks.onToggleShuffle,
            onCycleRepeat = callbacks.onCycleRepeat,
            onOpenQueue = callbacks.onOpenQueue,
            onOpenLyrics = callbacks.onOpenLyrics,
            onOpenSleepTimer = callbacks.onOpenSleepTimer,
            onClose = callbacks.onClose,
            onArtworkLongPress = callbacks.onArtworkLongPress,
            seekbarStyle = state.seekbarStyle,
            appearance = state.appearance,
            gestureConfig = state.gestureConfig,
            gestureController = gestureController,
            liveWaveform = state.waveformSamples,
            onToggleFavorite = callbacks.onToggleFavorite,
            onCyclePlayerStyle = callbacks.onCyclePlayerStyle,
            modifier = modifier
        )
        PlayerStyle.NEON -> NeonPlayer(state, callbacks, gestureController, modifier)
    }
}
