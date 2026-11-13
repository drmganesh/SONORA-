package ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VisualizerStyle
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import ui.theme.LocalEditorialColors

/**
 * Editorial Audio Visualizer Card for Now Playing:
 * - Real-time FFT frequency analysis from SonoraAudioEngine.
 * - Theme-aware colors derived from LocalEditorialColors.
 */
@Composable
fun NowPlayingVisualizerCard(
    frequencies: FloatArray,
    waveform: FloatArray,
    currentStyle: VisualizerStyle,
    onCycleStyle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(104.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onCycleStyle)
            .testTag("now_playing_visualizer_card"),
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.cardBorder),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            com.example.ui.components.AudioVisualizerView(
                frequencies = frequencies,
                waveform = waveform,
                style = currentStyle,
                primaryColor = colors.accentStrong,
                secondaryColor = colors.accentPill,
                modifier = Modifier.fillMaxSize()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FFT VISUALIZER • ${currentStyle.name.replace('_', ' ')}",
                    fontFamily = OutfitFontFamily,
                    color = colors.accentStrong,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = "Tap to switch",
                    fontFamily = PlusJakartaFontFamily,
                    color = colors.textSecondary,
                    fontSize = 10.sp
                )
            }
        }
    }
}
