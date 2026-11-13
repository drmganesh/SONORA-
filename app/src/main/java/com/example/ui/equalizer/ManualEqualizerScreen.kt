package com.example.ui.equalizer

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.equalizer.DynamicSoundMapper
import com.example.ui.theme.PlusJakartaFontFamily
import ui.theme.LocalEditorialColors

/**
 * Manual 15-Band Equalizer Sliders — SONORA AESTHETIC UI.
 */
@Composable
fun ManualEqualizerScreen(
    bandGains: FloatArray,
    isEnabled: Boolean,
    onGainChanged: (Int, Float) -> Unit,
    onResetAllBands: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val colors = LocalEditorialColors.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surface)
            .border(0.75.dp, colors.cardBorder, RoundedCornerShape(22.dp))
            .padding(vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "15-BAND PARAMETRIC EQUALIZER",
                    fontFamily = PlusJakartaFontFamily,
                    color = colors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = "Tap or drag sliders • Double-tap to reset to 0 dB",
                    fontFamily = PlusJakartaFontFamily,
                    color = colors.textSecondary,
                    fontSize = 11.sp
                )
            }

            OutlinedButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onResetAllBands()
                },
                enabled = isEnabled,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = colors.textPrimary
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text("Zero All", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until 15) {
                val gain = bandGains.getOrElse(i) { 0f }
                val label = DynamicSoundMapper.ISO_15_LABELS[i]

                SingleBandVerticalSlider(
                    frequencyLabel = label,
                    gainDb = gain,
                    isEnabled = isEnabled,
                    onGainChanged = { newGain -> onGainChanged(i, newGain) },
                    onReset = { onGainChanged(i, 0f) }
                )
            }
        }
    }
}

@Composable
private fun SingleBandVerticalSlider(
    frequencyLabel: String,
    gainDb: Float,
    isEnabled: Boolean,
    onGainChanged: (Float) -> Unit,
    onReset: () -> Unit
) {
    val view = LocalView.current
    val colors = LocalEditorialColors.current
    val accentColor = if (isEnabled) colors.accentStrong else colors.textMuted
    val trackBg = colors.surfaceTinted
    val zeroLineColor = colors.textMuted.copy(alpha = 0.45f)

    Column(
        modifier = Modifier
            .width(52.dp)
            .height(230.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        val formattedGain = when {
            gainDb > 0.05f -> "+${String.format("%.1f", gainDb)}"
            gainDb < -0.05f -> String.format("%.1f", gainDb)
            else -> "0.0"
        }

        Text(
            text = formattedGain,
            color = if (gainDb != 0f && isEnabled) accentColor else colors.textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Box(
            modifier = Modifier
                .width(44.dp)
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(trackBg)
                .border(0.75.dp, colors.cardBorder, RoundedCornerShape(14.dp))
                .pointerInput(isEnabled) {
                    if (!isEnabled) return@pointerInput
                    detectTapGestures(
                        onTap = { offset ->
                            val fraction = 1f - (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                            val newGain = (fraction * 24f - 12f)
                            val rounded = (Math.round(newGain * 2f) / 2f).coerceIn(-12f, 12f)
                            onGainChanged(rounded)
                        },
                        onDoubleTap = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onReset()
                        }
                    )
                }
                .pointerInput(isEnabled) {
                    if (!isEnabled) return@pointerInput
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            val fraction = 1f - (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                            val newGain = (fraction * 24f - 12f)
                            val rounded = (Math.round(newGain * 2f) / 2f).coerceIn(-12f, 12f)
                            onGainChanged(rounded)
                        },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            val offset = change.position
                            val fraction = 1f - (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                            val newGain = (fraction * 24f - 12f)
                            val rounded = (Math.round(newGain * 2f) / 2f).coerceIn(-12f, 12f)
                            onGainChanged(rounded)
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val widthPx = size.width
                val heightPx = size.height
                val centerY = heightPx / 2f
                val clampedGain = gainDb.coerceIn(-12f, 12f)

                drawLine(
                    color = zeroLineColor,
                    start = Offset(4f, centerY),
                    end = Offset(widthPx - 4f, centerY),
                    strokeWidth = 1.5.dp.toPx()
                )

                val trackX = widthPx / 2f
                drawLine(
                    color = zeroLineColor.copy(alpha = 0.25f),
                    start = Offset(trackX, 8f),
                    end = Offset(trackX, heightPx - 8f),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )

                val gainFraction = (clampedGain + 12f) / 24f
                val thumbY = (1f - gainFraction) * (heightPx - 32.dp.toPx()) + 16.dp.toPx()

                if (clampedGain != 0f && isEnabled) {
                    val topY = minOf(centerY, thumbY)
                    val barHeight = kotlin.math.abs(thumbY - centerY)
                    drawRoundRect(
                        color = accentColor,
                        topLeft = Offset(trackX - 2.5.dp.toPx(), topY),
                        size = Size(5.dp.toPx(), barHeight),
                        cornerRadius = CornerRadius(2.5.dp.toPx())
                    )
                }

                val thumbRadiusX = 14.dp.toPx()
                val thumbRadiusY = 8.dp.toPx()

                if (isEnabled) {
                    drawRoundRect(
                        color = accentColor.copy(alpha = 0.24f),
                        topLeft = Offset(trackX - thumbRadiusX - 2.dp.toPx(), thumbY - thumbRadiusY - 2.dp.toPx()),
                        size = Size((thumbRadiusX + 2.dp.toPx()) * 2f, (thumbRadiusY + 2.dp.toPx()) * 2f),
                        cornerRadius = CornerRadius(7.dp.toPx())
                    )
                    drawRoundRect(
                        color = accentColor,
                        topLeft = Offset(trackX - thumbRadiusX, thumbY - thumbRadiusY),
                        size = Size(thumbRadiusX * 2f, thumbRadiusY * 2f),
                        cornerRadius = CornerRadius(6.dp.toPx())
                    )
                } else {
                    drawRoundRect(
                        color = colors.textMuted.copy(alpha = 0.35f),
                        topLeft = Offset(trackX - thumbRadiusX, thumbY - thumbRadiusY),
                        size = Size(thumbRadiusX * 2f, thumbRadiusY * 2f),
                        cornerRadius = CornerRadius(6.dp.toPx())
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = frequencyLabel,
            color = colors.textPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
