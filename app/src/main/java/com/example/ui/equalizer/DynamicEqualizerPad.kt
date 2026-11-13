package com.example.ui.equalizer

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PlusJakartaFontFamily
import ui.theme.LocalEditorialColors
import ui.theme.LocalReduceAnimations
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Dynamic Sound Control Pad — SONORA AESTHETIC UI.
 * 11x9 interactive dot-matrix sound-shaping pad supporting light and dark editorial themes.
 */
@Composable
fun DynamicEqualizerPad(
    tilt: Float,
    contour: Float,
    isEnabled: Boolean,
    onPositionChanged: (Float, Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val colors = LocalEditorialColors.current
    val reduceAnimations = LocalReduceAnimations.current

    val accentColor = if (isEnabled) colors.accentStrong else colors.textMuted
    val pillAccent = if (isEnabled) colors.accentPill else colors.textMuted

    val animatedTilt = remember { Animatable(tilt) }
    val animatedContour = remember { Animatable(contour) }

    LaunchedEffect(tilt, contour, reduceAnimations) {
        if (reduceAnimations) {
            animatedTilt.snapTo(tilt)
            animatedContour.snapTo(contour)
        } else {
            animatedTilt.animateTo(tilt, tween(120, easing = FastOutSlowInEasing))
            animatedContour.animateTo(contour, tween(120, easing = FastOutSlowInEasing))
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pad_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = if (reduceAnimations) 1f else 0.95f,
        targetValue = if (reduceAnimations) 1f else 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "FOCUSED",
                fontFamily = PlusJakartaFontFamily,
                color = if (isEnabled && contour > 0.15f) accentColor else colors.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "W\nA\nR\nM",
                fontFamily = PlusJakartaFontFamily,
                color = if (isEnabled && tilt < -0.15f) accentColor else colors.textMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 13.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1.22f)
                    .clip(RoundedCornerShape(26.dp))
                    .background(colors.surface)
                    .border(
                        width = 1.dp,
                        color = if (isEnabled) accentColor.copy(alpha = 0.32f) else colors.cardBorder,
                        shape = RoundedCornerShape(26.dp)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(isEnabled) {
                            if (!isEnabled) return@pointerInput
                            detectTapGestures(
                                onDoubleTap = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onReset()
                                }
                            )
                        }
                        .pointerInput(isEnabled) {
                            if (!isEnabled) return@pointerInput
                            detectDragGestures(
                                onDragStart = { offset ->
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    val newTilt = ((offset.x / size.width.toFloat()) * 2f - 1f).coerceIn(-1f, 1f)
                                    val newContour = -((offset.y / size.height.toFloat()) * 2f - 1f).coerceIn(-1f, 1f)
                                    onPositionChanged(newTilt, newContour)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val offset = change.position
                                    val newTilt = ((offset.x / size.width.toFloat()) * 2f - 1f).coerceIn(-1f, 1f)
                                    val newContour = -((offset.y / size.height.toFloat()) * 2f - 1f).coerceIn(-1f, 1f)
                                    onPositionChanged(newTilt, newContour)
                                }
                            )
                        }
                ) {
                    val currentTilt = animatedTilt.value
                    val currentContour = animatedContour.value
                    val dotBase = colors.textMuted

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val widthPx = size.width
                        val heightPx = size.height

                        val cols = 11
                        val rows = 9

                        val colSpacing = widthPx / (cols + 1)
                        val rowSpacing = heightPx / (rows + 1)

                        val thumbX = ((currentTilt + 1f) / 2f) * widthPx
                        val thumbY = ((-currentContour + 1f) / 2f) * heightPx

                        val centerX = widthPx / 2f
                        val centerY = heightPx / 2f
                        drawLine(
                            color = accentColor.copy(alpha = 0.14f),
                            start = Offset(colSpacing, centerY),
                            end = Offset(widthPx - colSpacing, centerY),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = accentColor.copy(alpha = 0.14f),
                            start = Offset(centerX, rowSpacing),
                            end = Offset(centerX, heightPx - rowSpacing),
                            strokeWidth = 1.dp.toPx()
                        )

                        for (c in 1..cols) {
                            val dotX = c * colSpacing
                            for (r in 1..rows) {
                                val dotY = r * rowSpacing
                                val distToThumb = hypot(dotX - thumbX, dotY - thumbY)
                                val isCenterDot = (c == 6 && r == 5)

                                val maxInfluence = colSpacing * 2.5f
                                val proximity = (1f - (distToThumb / maxInfluence)).coerceIn(0f, 1f)

                                val dotRadius = when {
                                    isCenterDot -> 3.2.dp.toPx()
                                    proximity > 0f -> (2.0 + proximity * 2.8).dp.toPx()
                                    else -> 1.8.dp.toPx()
                                }

                                val dotColor = when {
                                    isCenterDot && currentTilt == 0f && currentContour == 0f -> accentColor
                                    proximity > 0f && isEnabled -> {
                                        androidx.compose.ui.graphics.lerp(
                                            dotBase.copy(alpha = 0.28f),
                                            accentColor.copy(alpha = 0.95f),
                                            proximity
                                        )
                                    }
                                    isCenterDot -> accentColor.copy(alpha = 0.5f)
                                    else -> dotBase.copy(alpha = 0.25f)
                                }

                                drawCircle(
                                    color = dotColor,
                                    radius = dotRadius,
                                    center = Offset(dotX, dotY)
                                )
                            }
                        }

                        if (isEnabled) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        accentColor.copy(alpha = 0.42f),
                                        pillAccent.copy(alpha = 0.18f),
                                        Color.Transparent
                                    ),
                                    center = Offset(thumbX, thumbY),
                                    radius = 34.dp.toPx() * pulseScale
                                ),
                                radius = 34.dp.toPx() * pulseScale,
                                center = Offset(thumbX, thumbY)
                            )

                            drawCircle(
                                color = accentColor,
                                radius = 17.dp.toPx(),
                                center = Offset(thumbX, thumbY),
                                style = Stroke(width = 2.dp.toPx())
                            )

                            drawCircle(
                                color = accentColor,
                                radius = 7.dp.toPx(),
                                center = Offset(thumbX, thumbY)
                            )
                        } else {
                            drawCircle(
                                color = dotBase.copy(alpha = 0.4f),
                                radius = 12.dp.toPx(),
                                center = Offset(thumbX, thumbY),
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }
                    }
                }
            }

            Text(
                text = "B\nR\nI\nG\nH\nT",
                fontFamily = PlusJakartaFontFamily,
                color = if (isEnabled && tilt > 0.15f) accentColor else colors.textMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 13.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "BROAD",
                fontFamily = PlusJakartaFontFamily,
                color = if (isEnabled && contour < -0.15f) accentColor else colors.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = colors.surfaceTinted,
            border = androidx.compose.foundation.BorderStroke(0.75.dp, colors.cardBorder),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tiltInt = (tilt * 100f).roundToInt()
                val contourInt = (contour * 100f).roundToInt()

                val tiltLabel = when {
                    tiltInt < -10 -> "Tilt: $tiltInt (Warm)"
                    tiltInt > 10 -> "Tilt: +$tiltInt (Bright)"
                    else -> "Tilt: 0 (Neutral)"
                }

                val contourLabel = when {
                    contourInt > 10 -> "Contour: +$contourInt (Focused)"
                    contourInt < -10 -> "Contour: $contourInt (Broad)"
                    else -> "Contour: 0 (Balanced)"
                }

                Text(
                    text = tiltLabel,
                    color = if (tiltInt != 0 && isEnabled) accentColor else colors.textPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )

                Text(
                    text = "•",
                    color = colors.textMuted,
                    fontSize = 12.sp
                )

                Text(
                    text = contourLabel,
                    color = if (contourInt != 0 && isEnabled) accentColor else colors.textPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
