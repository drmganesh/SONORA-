package ui.lyrics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AvTimer
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import domain.lyrics.LyricsTimingState
import ui.motion.rememberMotionAwareHaptic
import ui.theme.EditorialColors
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsTimingSheet(
    timingState: LyricsTimingState,
    accentColor: Color,
    colors: EditorialColors,
    onAdjustDelta: (Long) -> Unit,
    onSetOffset: (Long) -> Unit,
    onReset: () -> Unit,
    onSaveAsGlobalDefault: (Long) -> Unit,
    onUseGlobalDefault: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val performHaptic = rememberMotionAwareHaptic()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("lyrics_timing_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AvTimer,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "LYRICS TIMING",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.2.sp,
                            color = accentColor
                        )
                        Text(
                            text = "Fine-tune synchronization",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 13.sp,
                            color = colors.textSecondary
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("lyrics_timing_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close timing controls",
                        tint = colors.textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Readout + Status Pill
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = colors.surfaceElevated,
                border = BorderStroke(1.dp, colors.cardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = timingState.formattedOffset(),
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 32.sp,
                        color = if (timingState.effectiveOffsetMs == 0L) colors.textPrimary else accentColor,
                        modifier = Modifier.testTag("lyrics_timing_offset_value")
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = accentColor.copy(alpha = 0.16f),
                        border = BorderStroke(0.75.dp, accentColor.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = when {
                                    timingState.effectiveOffsetMs > 0L -> Icons.Rounded.FastForward
                                    timingState.effectiveOffsetMs < 0L -> Icons.Rounded.FastRewind
                                    else -> Icons.Rounded.CheckCircle
                                },
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = timingState.statusLabel,
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = colors.textPrimary,
                                modifier = Modifier.testTag("lyrics_timing_status_label")
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Step buttons: -500 ms, -100 ms, Reset, +100 ms, +500 ms
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TimingStepButton(
                    label = "-500ms",
                    colors = colors,
                    accentColor = accentColor,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("lyrics_timing_minus_500"),
                    onClick = {
                        performHaptic(HapticFeedbackType.TextHandleMove)
                        onAdjustDelta(-500L)
                    }
                )
                TimingStepButton(
                    label = "-100ms",
                    colors = colors,
                    accentColor = accentColor,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("lyrics_timing_minus_100"),
                    onClick = {
                        performHaptic(HapticFeedbackType.TextHandleMove)
                        onAdjustDelta(-100L)
                    }
                )
                Surface(
                    onClick = {
                        performHaptic(HapticFeedbackType.TextHandleMove)
                        onReset()
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = accentColor.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("lyrics_timing_reset")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = "Reset offset",
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                TimingStepButton(
                    label = "+100ms",
                    colors = colors,
                    accentColor = accentColor,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("lyrics_timing_plus_100"),
                    onClick = {
                        performHaptic(HapticFeedbackType.TextHandleMove)
                        onAdjustDelta(100L)
                    }
                )
                TimingStepButton(
                    label = "+500ms",
                    colors = colors,
                    accentColor = accentColor,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("lyrics_timing_plus_500"),
                    onClick = {
                        performHaptic(HapticFeedbackType.TextHandleMove)
                        onAdjustDelta(500L)
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Fine range slider: -10s to +10s
            Slider(
                value = timingState.effectiveOffsetMs.toFloat(),
                onValueChange = { raw ->
                    val snapped = ((raw / 50f).roundToLong() * 50L)
                        .coerceIn(LyricsTimingState.MIN_OFFSET_MS, LyricsTimingState.MAX_OFFSET_MS)
                    onSetOffset(snapped)
                },
                valueRange = LyricsTimingState.MIN_OFFSET_MS.toFloat()..LyricsTimingState.MAX_OFFSET_MS.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = colors.cardBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("lyrics_timing_slider")
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "-10.0s (Behind)",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 11.sp,
                    color = colors.textMuted
                )
                Text(
                    text = "0.0s",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 11.sp,
                    color = colors.textMuted
                )
                Text(
                    text = "+10.0s (Ahead)",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 11.sp,
                    color = colors.textMuted
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = colors.cardBorder.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(12.dp))

            // Per-track & Global Default options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        performHaptic(HapticFeedbackType.TextHandleMove)
                        onSaveAsGlobalDefault(timingState.effectiveOffsetMs)
                    },
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("lyrics_timing_save_global")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Public,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Set Global Default",
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = colors.textPrimary
                    )
                }

                if (timingState.hasCustomTrackOffset && timingState.globalOffsetMs != timingState.effectiveOffsetMs) {
                    TextButton(
                        onClick = onUseGlobalDefault,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("lyrics_timing_use_global")
                    ) {
                        Text(
                            text = "Use Global (${if (timingState.globalOffsetMs > 0) "+" else ""}${timingState.globalOffsetMs}ms)",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = accentColor
                        )
                    }
                } else {
                    Text(
                        text = "Saved per track",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 11.5.sp,
                        color = colors.textMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun TimingStepButton(
    label: String,
    colors: EditorialColors,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.cardBorder),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (label.startsWith("+")) accentColor else colors.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
