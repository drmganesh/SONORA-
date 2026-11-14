package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.model.SpatialAudioConfig
import com.example.model.SpatialMode
import com.example.ui.theme.SonoraCardBorder
import com.example.ui.theme.SonoraCyan
import com.example.ui.theme.SonoraCyanGlow
import com.example.ui.theme.SonoraGold
import com.example.ui.theme.SonoraObsidian
import com.example.ui.theme.SonoraSurfaceDark
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SpatialSoundstageView(
    spatialConfig: SpatialAudioConfig,
    onPositionChanged: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ui.theme.LocalEditorialColors.current
    val dynPalette = ui.theme.LocalDynamicColorPalette.current
    val primaryAccent = colors.accentStrong
    val secondaryAccent = dynPalette.tertiary
    val radarSurfaceInner = colors.surfaceTinted
    val radarSurfaceOuter = colors.surface
    val waveAnim = remember { Animatable(0f) }

    LaunchedEffect(spatialConfig.isEnabled) {
        if (spatialConfig.isEnabled) {
            waveAnim.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2400, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(spatialConfig.isEnabled) {
                    if (!spatialConfig.isEnabled) return@pointerInput
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val halfW = size.width / 2
                        val halfH = size.height / 2
                        val newX = ((spatialConfig.listenerX * halfW) + dragAmount.x) / halfW
                        val newY = ((spatialConfig.listenerY * halfH) + dragAmount.y) / halfH
                        onPositionChanged(
                            newX.coerceIn(-0.85f, 0.85f),
                            newY.coerceIn(-0.85f, 0.85f)
                        )
                    }
                }
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val maxRadius = (minOf(size.width, size.height) / 2) - 16.dp.toPx()

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(radarSurfaceInner, radarSurfaceOuter),
                    center = center,
                    radius = maxRadius
                ),
                radius = maxRadius,
                center = center
            )

            val numRings = 4
            for (i in 1..numRings) {
                val ringRad = maxRadius * (i.toFloat() / numRings) * spatialConfig.soundstageWidth.coerceIn(0.4f, 1f)
                drawCircle(
                    color = colors.cardBorder.copy(alpha = 0.65f),
                    radius = ringRad,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            drawLine(
                color = colors.cardBorder.copy(alpha = 0.45f),
                start = Offset(center.x - maxRadius, center.y),
                end = Offset(center.x + maxRadius, center.y),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = colors.cardBorder.copy(alpha = 0.45f),
                start = Offset(center.x, center.y - maxRadius),
                end = Offset(center.x, center.y + maxRadius),
                strokeWidth = 1.dp.toPx()
            )

            val speakerAngles = when (spatialConfig.mode) {
                SpatialMode.HEADPHONES -> listOf(-Math.PI / 4, Math.PI / 4)
                SpatialMode.SPEAKERS -> listOf(-Math.PI / 6, Math.PI / 6, -5 * Math.PI / 6, 5 * Math.PI / 6)
                SpatialMode.CINEMA_360 -> listOf(
                    -Math.PI / 6, 0.0, Math.PI / 6,
                    -Math.PI / 2, Math.PI / 2,
                    -5 * Math.PI / 6, 5 * Math.PI / 6
                )
            }

            for (angle in speakerAngles) {
                val sx = center.x + maxRadius * cos(angle).toFloat() * 0.88f
                val sy = center.y + maxRadius * sin(angle).toFloat() * 0.88f
                val speakerPos = Offset(sx, sy)

                if (spatialConfig.isEnabled) {
                    val pulseRadius = 12.dp.toPx() + (waveAnim.value * 28.dp.toPx())
                    drawCircle(
                        color = secondaryAccent.copy(alpha = (1f - waveAnim.value) * 0.45f * spatialConfig.intensity),
                        radius = pulseRadius,
                        center = speakerPos,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(secondaryAccent.copy(alpha = 0.9f), secondaryAccent),
                        center = speakerPos,
                        radius = 8.dp.toPx()
                    ),
                    radius = 6.dp.toPx(),
                    center = speakerPos
                )
            }

            val listenerPx = Offset(
                center.x + (spatialConfig.listenerX * maxRadius * 0.75f),
                center.y + (spatialConfig.listenerY * maxRadius * 0.75f)
            )

            if (spatialConfig.isEnabled) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(primaryAccent.copy(alpha = 0.45f), Color.Transparent),
                        center = listenerPx,
                        radius = 24.dp.toPx()
                    ),
                    radius = 24.dp.toPx(),
                    center = listenerPx
                )
            }

            drawCircle(
                color = primaryAccent,
                radius = 10.dp.toPx(),
                center = listenerPx
            )
            drawCircle(
                color = colors.onAccentStrong,
                radius = 4.dp.toPx(),
                center = listenerPx
            )
        }
    }
}
