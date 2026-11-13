package ui.player

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.ArtworkAnimationOption
import com.example.model.PlaybackInfo
import com.example.model.PlayerAppearanceConfig
import com.example.model.PlayerGestureConfig
import com.example.model.Song
import com.example.model.VisualizerStyle
import ui.player.seekbar.SeekbarStyle
import ui.theme.DynamicColorPalette
import ui.theme.EditorialColors

@Immutable
data class PlayerStyleCallbacks(
    val onTogglePlayPause: () -> Unit,
    val onPlayNext: () -> Unit,
    val onPlayPrevious: () -> Unit,
    val onSeekTo: (Long) -> Unit,
    val onToggleShuffle: () -> Unit,
    val onCycleRepeat: () -> Unit,
    val onOpenQueue: () -> Unit,
    val onOpenLyrics: () -> Unit,
    val onOpenSleepTimer: () -> Unit,
    val onClose: () -> Unit,
    val onArtworkLongPress: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onOpenAudioEffects: () -> Unit,
    val onOpenEqualizer: () -> Unit,
    val onOpenOutputDevice: () -> Unit,
    val onToggleFocusMode: () -> Unit,
    val onOpenCustomization: () -> Unit,
    val onCyclePlayerStyle: () -> Unit,
    val onToggleVisualizer: () -> Unit,
    val onCycleVisualizerStyle: () -> Unit
)

@Immutable
data class PlayerRenderState(
    val song: Song?,
    val playbackInfo: PlaybackInfo,
    val seekbarStyle: SeekbarStyle,
    val appearance: PlayerAppearanceConfig,
    val gestureConfig: PlayerGestureConfig,
    val dynamicPalette: DynamicColorPalette,
    val frequencies: FloatArray,
    val waveformSamples: FloatArray,
    val isVisualizerActive: Boolean,
    val visualizerStyle: VisualizerStyle,
    val isNowPlayingActive: Boolean = true
)

object PlayerStyleManager {

    fun resolveAccentColor(
        appearance: PlayerAppearanceConfig,
        colors: EditorialColors,
        palette: DynamicColorPalette
    ): Color {
        return if (appearance.useDynamicAlbumAccent) colors.accentStrong else palette.primary
    }

    fun resolveCornerRadius(
        appearance: PlayerAppearanceConfig,
        styleDefault: Dp = 28.dp
    ): Dp {
        return if (appearance == PlayerAppearanceConfig.DEFAULT) {
            styleDefault
        } else {
            appearance.cornerRadius.radiusDp.dp
        }
    }

    fun resolveArtworkFraction(appearance: PlayerAppearanceConfig): Float {
        return appearance.artworkSize.fraction
    }

    fun shouldSpinVinyl(appearance: PlayerAppearanceConfig, styleRequiresSpin: Boolean): Boolean {
        return styleRequiresSpin || appearance.artworkAnimation == ArtworkAnimationOption.VINYL_SPIN
    }

    fun formatTrackQualityBadge(song: Song?): String {
        if (song == null) return "LOCAL AUDIO"
        return song.audioFormatInfo
    }

    fun formatBitrateSampleSummary(song: Song?): String {
        if (song == null) return ""
        val sampleKhz = if (song.sampleRateHz >= 1000) {
            val khz = song.sampleRateHz / 1000f
            if (khz % 1f == 0f) "${khz.toInt()} kHz" else String.format(java.util.Locale.US, "%.1f kHz", khz)
        } else {
            "${song.sampleRateHz} Hz"
        }
        return "${song.bitrateKbps} kbps • $sampleKhz • ${song.bitDepth}-bit"
    }
}
