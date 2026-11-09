package ui.player.seekbar

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.SonoraCharcoal
import com.example.ui.theme.SonoraDustyRose

/**
 * Style 8: Material 3 Seekbar
 * Native Material Design 3 slider with consistent theme colors.
 */
@Composable
fun Material3Seekbar(
    progress: Float,
    onSeekProgress: (Float) -> Unit,
    onSeekConfirmed: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    enabled: Boolean = true,
    accentColor: Color = SonoraDustyRose,
    inactiveColor: Color = SonoraCharcoal
) {
    Slider(
        value = progress.coerceIn(0f, 1f),
        onValueChange = { onSeekProgress(it) },
        onValueChangeFinished = { onSeekConfirmed(progress) },
        enabled = enabled,
        colors = SliderDefaults.colors(
            thumbColor = accentColor,
            activeTrackColor = accentColor,
            inactiveTrackColor = inactiveColor
        ),
        modifier = modifier.fillMaxWidth()
    )
}
