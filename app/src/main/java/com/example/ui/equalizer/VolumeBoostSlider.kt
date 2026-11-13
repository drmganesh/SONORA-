package com.example.ui.equalizer

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PlusJakartaFontFamily
import ui.theme.LocalEditorialColors

/**
 * Volume Boost Preamp Slider — SONORA AESTHETIC UI.
 */
@Composable
fun VolumeBoostSlider(
    boostDb: Float,
    isEnabled: Boolean,
    onBoostChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val colors = LocalEditorialColors.current
    val accentColor = if (isEnabled) colors.accentStrong else colors.textMuted

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surface)
            .border(0.75.dp, colors.cardBorder, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "VOLUME BOOST",
                    fontFamily = PlusJakartaFontFamily,
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = if (boostDb > 0f) "Digital Preamp • Soft Limiting Active" else "Unity Gain • 0 dB Reference",
                    fontFamily = PlusJakartaFontFamily,
                    color = colors.textSecondary,
                    fontSize = 11.sp
                )
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (boostDb > 0f && isEnabled) colors.accentPill else colors.surfaceTinted
            ) {
                val formatted = if (boostDb <= 0.05f) "0.0 dB" else "+${String.format("%.1f", boostDb)} dB"
                Text(
                    text = formatted,
                    color = if (boostDb > 0f && isEnabled) colors.onAccentPill else colors.textMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Slider(
            value = boostDb,
            onValueChange = { newValue ->
                val rounded = (Math.round(newValue * 2f) / 2f)
                if (rounded != boostDb) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onBoostChanged(rounded)
                }
            },
            valueRange = 0f..10f,
            steps = 19,
            enabled = isEnabled,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = colors.cardBorder,
                disabledThumbColor = colors.textMuted,
                disabledActiveTrackColor = colors.textMuted.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("0 dB (Neutral)", color = colors.textMuted, fontSize = 10.sp)
            Text("+5 dB", color = colors.textMuted, fontSize = 10.sp)
            Text("+10 dB (Max)", color = colors.textMuted, fontSize = 10.sp)
        }
    }
}
