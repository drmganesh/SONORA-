package ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.model.PlayerAppearanceConfig
import ui.player.seekbar.SeekbarRenderer
import ui.player.seekbar.SeekbarStyle
import ui.theme.LocalEditorialColors

/**
 * Shared adaptive seekbar component used across all Player Styles.
 */
@Composable
fun AdaptiveSeekbar(
    currentPositionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    isPlaying: Boolean,
    style: SeekbarStyle,
    modifier: Modifier = Modifier,
    appearance: PlayerAppearanceConfig = PlayerAppearanceConfig.DEFAULT,
    liveWaveform: FloatArray? = null,
    hapticFeedbackEnabled: Boolean = true,
    showTimestamps: Boolean = true,
    accentColor: Color = LocalEditorialColors.current.accentStrong,
    inactiveColor: Color = LocalEditorialColors.current.cardBorder
) {
    SeekbarRenderer(
        currentPositionMs = currentPositionMs,
        durationMs = durationMs,
        onSeekTo = onSeekTo,
        isPlaying = isPlaying,
        style = style,
        modifier = modifier,
        showTimestamps = showTimestamps,
        accentColor = accentColor,
        inactiveColor = inactiveColor,
        liveWaveform = liveWaveform,
        thicknessScale = appearance.seekbarThicknessScale,
        hapticFeedbackEnabled = hapticFeedbackEnabled
    )
}
