package ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.model.PlayerAppearanceConfig
import ui.player.seekbar.LocalPlaybackSeekCapability
import ui.player.seekbar.SeekbarRenderer
import ui.player.seekbar.SeekbarStyle
import ui.theme.LocalEditorialColors

/**
 * Adaptive seekbar entry point used across every Player Style.
 * Automatically honors the user's selected SeekbarStyle, PlayerAppearanceConfig thickness,
 * real Media3 seekability / live-stream state via [LocalPlaybackSeekCapability],
 * live preview timestamps, and dynamic accent theming.
 */
@Composable
fun AdaptiveSeekbar(
    currentPositionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    isPlaying: Boolean,
    style: SeekbarStyle,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showTimestamps: Boolean = true,
    accentColor: Color = LocalEditorialColors.current.accentStrong,
    inactiveColor: Color = LocalEditorialColors.current.cardBorder,
    liveWaveform: FloatArray? = null,
    appearance: PlayerAppearanceConfig? = null,
    thicknessScale: Float = appearance?.seekbarThicknessScale ?: 1.0f,
    hapticFeedbackEnabled: Boolean = true,
    isSeekable: Boolean = LocalPlaybackSeekCapability.current.isSeekable,
    isLiveStream: Boolean = LocalPlaybackSeekCapability.current.isLiveStream
) {
    SeekbarRenderer(
        currentPositionMs = currentPositionMs,
        durationMs = durationMs,
        onSeekTo = onSeekTo,
        isPlaying = isPlaying,
        style = style,
        modifier = modifier,
        enabled = enabled,
        showTimestamps = showTimestamps,
        accentColor = accentColor,
        inactiveColor = inactiveColor,
        liveWaveform = liveWaveform,
        thicknessScale = thicknessScale,
        hapticFeedbackEnabled = hapticFeedbackEnabled,
        isSeekable = isSeekable,
        isLiveStream = isLiveStream
    )
}
