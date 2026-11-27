package ui.pet

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import domain.pet.MayurAnimationQuality
import domain.pet.MayurBonePose
import domain.pet.MayurCostume
import domain.pet.MayurExpression
import domain.pet.MayurReactionType
import domain.pet.ProjectedPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 3D-space projected environment costumes (weather, nighttime, festivals) and reaction/beat cues
 * rendered in harmony with the procedural 3D MAYUR peacock rig.
 */
object Mayur3DCostumeOverlay {

    fun drawBackdropAura(
        scope: DrawScope,
        costume: MayurCostume,
        pose: MayurBonePose,
        quality: MayurAnimationQuality
    ) = with(scope) {
        if (!quality.allowAmbientGlow && !costume.isFestival && !pose.isSleeping) return

        val center = Offset(size.width * 0.50f, size.height * 0.55f)
        val pulse = 1f + sin(pose.elapsedTimeSec * 2.2f) * 0.04f + pose.beatPulse * 0.14f
        val radius = size.minDimension * 0.47f * pulse

        val glowColor = when (costume) {
            MayurCostume.DAYTIME -> Color(0xFFFFCA45)
            MayurCostume.NIGHTTIME -> Color(0xFF4B6CB7)
            MayurCostume.RAIN -> Color(0xFF4FC3F7)
            MayurCostume.COLD -> Color(0xFFFFA726)
            MayurCostume.HOT_SUNNY -> Color(0xFFFFD54F)
            MayurCostume.WIND -> Color(0xFF26C6DA)
            MayurCostume.SNOWFALL -> Color(0xFFB3E5FC)
            MayurCostume.THUNDERSTORM -> Color(0xFF7E57C2)
            MayurCostume.FESTIVAL_DIWALI -> Color(0xFFFFB300)
            MayurCostume.FESTIVAL_HOLI -> Color(0xFFEC407A)
            MayurCostume.FESTIVAL_RATH_YATRA -> Color(0xFFFF7043)
            MayurCostume.FESTIVAL_DUSSEHRA -> Color(0xFFFF8F00)
            MayurCostume.FESTIVAL_KARTIK_PURNIMA -> Color(0xFFFFCA28)
            MayurCostume.FESTIVAL_GANESH -> Color(0xFFFF9800)
            MayurCostume.FESTIVAL_NAVRATRI -> Color(0xFFE91E63)
            MayurCostume.FESTIVAL_JANMASHTAMI -> Color(0xFF26C6DA)
            MayurCostume.FESTIVAL_HARVEST -> Color(0xFFFFB74D)
            MayurCostume.FESTIVAL_REGIONAL -> Color(0xFFFFC107)
        }

        val alpha = when {
            pose.isSleeping -> 0.18f
            costume.isFestival -> 0.28f
            pose.isDancing -> (0.20f + pose.audioAmplitude * 0.16f + pose.beatPulse * 0.10f).coerceAtMost(0.42f)
            else -> 0.15f
        }

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    glowColor.copy(alpha = alpha),
                    glowColor.copy(alpha = alpha * 0.35f),
                    Color.Transparent
                ),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )

        // Subtle beat-synchronized audio ring while dancing
        if (pose.isDancing && quality != MayurAnimationQuality.BATTERY_SAVER) {
            val ringRadius = size.minDimension * (0.37f + pose.beatPulse * 0.08f)
            drawCircle(
                color = Color(0xFFFFD54F).copy(alpha = (0.12f + pose.beatPulse * 0.28f).coerceAtMost(0.42f)),
                radius = ringRadius,
                center = center,
                style = Stroke(width = size.minDimension * 0.011f)
            )
        }
    }

    /**
     * Draws 3D head-locked sunglasses when Hot Sunny costume is active and face is visible.
     */
    fun drawHeadLockedSunglasses(
        scope: DrawScope,
        leftEye: ProjectedPoint,
        rightEye: ProjectedPoint,
        headForwardZ: Float,
        scale: Float
    ) = with(scope) {
        if (headForwardZ <= 0.12f) return
        val midX = (leftEye.sx + rightEye.sx) * 0.5f
        val midY = (leftEye.sy + rightEye.sy) * 0.5f
        val eyeSpan = kotlin.math.hypot(rightEye.sx - leftEye.sx, rightEye.sy - leftEye.sy)
        val angleDeg = Math.toDegrees(
            kotlin.math.atan2((rightEye.sy - leftEye.sy).toDouble(), (rightEye.sx - leftEye.sx).toDouble())
        ).toFloat()

        val lensW = (eyeSpan * 0.72f).coerceAtLeast(scale * 0.16f)
        val lensH = scale * 0.145f
        val r = CornerRadius(lensH * 0.42f, lensH * 0.42f)

        rotate(degrees = angleDeg, pivot = Offset(midX, midY)) {
            val leftTopLeft = Offset(midX - eyeSpan * 0.5f - lensW * 0.5f, midY - lensH * 0.5f)
            val rightTopLeft = Offset(midX + eyeSpan * 0.5f - lensW * 0.5f, midY - lensH * 0.5f)
            val lensSize = Size(lensW, lensH)
            val tint = Brush.linearGradient(
                colors = listOf(Color(0xDD101C3D), Color(0xCC006064)),
                start = leftTopLeft,
                end = Offset(leftTopLeft.x + lensW, leftTopLeft.y + lensH)
            )
            drawRoundRect(tint, leftTopLeft, lensSize, r)
            drawRoundRect(tint, rightTopLeft, lensSize, r)
            val stroke = Stroke(width = (scale * 0.022f).coerceAtLeast(1.4f))
            drawRoundRect(Color(0xFFFFD54F), leftTopLeft, lensSize, r, style = stroke)
            drawRoundRect(Color(0xFFFFD54F), rightTopLeft, lensSize, r, style = stroke)
            drawLine(
                color = Color(0xFFFFD54F),
                start = Offset(leftTopLeft.x + lensW, midY - lensH * 0.1f),
                end = Offset(rightTopLeft.x, midY - lensH * 0.1f),
                strokeWidth = stroke.width
            )
        }
    }

    /**
     * Draws 3D collar scarf (Cold / Wind / Snow) or Marigold Garland (Rath Yatra / Festivals).
     */
    fun drawNeckAccessory(
        scope: DrawScope,
        costume: MayurCostume,
        neckPoint: ProjectedPoint,
        scale: Float
    ) = with(scope) {
        when (costume) {
            MayurCostume.COLD, MayurCostume.WIND, MayurCostume.SNOWFALL -> {
                val primary = when (costume) {
                    MayurCostume.COLD -> Color(0xFFD84315)
                    MayurCostume.WIND -> Color(0xFF00838F)
                    else -> Color(0xFF1565C0)
                }
                val stripe = if (costume == MayurCostume.SNOWFALL) Color(0xFFE1F5FE) else Color(0xFFFFD54F)
                val w = scale * 0.54f
                val h = scale * 0.14f
                val topLeft = Offset(neckPoint.sx - w * 0.5f, neckPoint.sy - h * 0.4f)
                drawRoundRect(primary, topLeft, Size(w, h), CornerRadius(h * 0.5f, h * 0.5f))
                drawRoundRect(
                    stripe,
                    topLeft,
                    Size(w, h),
                    CornerRadius(h * 0.5f, h * 0.5f),
                    style = Stroke(width = (scale * 0.02f).coerceAtLeast(1.2f))
                )
            }
            MayurCostume.FESTIVAL_RATH_YATRA,
            MayurCostume.FESTIVAL_GANESH,
            MayurCostume.FESTIVAL_NAVRATRI,
            MayurCostume.FESTIVAL_HARVEST,
            MayurCostume.FESTIVAL_REGIONAL -> {
                val beads = 5
                val span = scale * 0.44f
                for (i in 0 until beads) {
                    val f = i / (beads - 1f)
                    val bx = neckPoint.sx - span * 0.5f + f * span
                    val by = neckPoint.sy + sin(f * PI.toFloat()) * scale * 0.09f
                    val col = if (i % 2 == 0) Color(0xFFFF9800) else Color(0xFFFFD54F)
                    drawCircle(col, radius = scale * 0.042f, center = Offset(bx, by))
                }
            }
            else -> Unit
        }
    }

    /**
     * Draws foreground environment props (umbrella, Diwali diya, Kartik boita, snowflakes, rain,
     * sleeping zZz, and touch/dance sparkles & musical notes).
     */
    fun drawForegroundEffects(
        scope: DrawScope,
        costume: MayurCostume,
        pose: MayurBonePose,
        quality: MayurAnimationQuality
    ) = with(scope) {
        val w = size.width
        val h = size.height
        val phase = (pose.elapsedTimeSec * 0.45f) % 1f

        when (costume) {
            MayurCostume.RAIN -> drawRainAndUmbrella(w, h, phase, quality)
            MayurCostume.SNOWFALL -> drawSnowflakes(w, h, phase, quality)
            MayurCostume.WIND -> drawWindWisps(w, h, phase)
            MayurCostume.HOT_SUNNY -> drawFourPointStar(
                Offset(w * 0.86f, h * 0.14f),
                w * 0.04f * (0.8f + 0.2f * sin(pose.elapsedTimeSec * 3f)),
                Color(0xFFFFD54F)
            )
            MayurCostume.NIGHTTIME -> drawFourPointStar(
                Offset(w * 0.16f, h * 0.18f),
                w * 0.030f,
                Color(0xFFFFE082).copy(alpha = 0.8f)
            )
            MayurCostume.FESTIVAL_DIWALI -> drawDiwaliDiya(w, h, pose.elapsedTimeSec, quality)
            MayurCostume.FESTIVAL_HOLI -> drawHoliDots(w, h, pose.elapsedTimeSec, quality)
            MayurCostume.FESTIVAL_KARTIK_PURNIMA -> drawKartikBoita(w, h, pose.elapsedTimeSec)
            MayurCostume.FESTIVAL_DUSSEHRA,
            MayurCostume.FESTIVAL_RATH_YATRA,
            MayurCostume.FESTIVAL_JANMASHTAMI -> {
                val glow = 0.72f + 0.28f * sin(pose.elapsedTimeSec * 3.5f)
                drawFourPointStar(Offset(w * 0.84f, h * 0.16f), w * 0.034f * glow, Color(0xFFFFCA28))
                drawFourPointStar(Offset(w * 0.16f, h * 0.20f), w * 0.026f * glow, Color(0xFFFF7043))
            }
            else -> Unit
        }

        if (pose.isSleeping) {
            drawSleepingZzz(w, h, phase)
        }

        val react = pose.activeReactionType
        if (react != null && pose.reactionProgress > 0.01f) {
            val fade = (1f - (pose.reactionProgress - 0.72f).coerceAtLeast(0f) / 0.28f).coerceIn(0f, 1f)
            when (react) {
                MayurReactionType.HEAD_PAT_HAPPY ->
                    drawSparkleBurst(w, h, 0.56f, 0.16f, Color(0xFFFFD54F), fade, pose.reactionProgress)
                MayurReactionType.TAIL_FAN_SHAKE ->
                    drawSparkleBurst(w, h, 0.22f, 0.42f, Color(0xFF26C6DA), fade, pose.reactionProgress)
                MayurReactionType.DOUBLE_TAP_HAPPY,
                MayurReactionType.SPECIAL_LONG_PRESS -> {
                    drawSparkleBurst(w, h, 0.52f, 0.14f, Color(0xFFFFCA28), fade, pose.reactionProgress)
                    drawFloatingMusicNotes(w, h, fade, pose.reactionProgress, quality.maxAccentParticles.coerceAtMost(3))
                }
                MayurReactionType.SLEEPY_WAKE_IRRITATED ->
                    drawSleepyGrump(w, h, pose.expression == MayurExpression.IRRITATED, fade)
                MayurReactionType.ADJUST_HEADPHONES ->
                    drawSparkleBurst(w, h, 0.78f, 0.30f, Color(0xFFFFD54F), fade, pose.reactionProgress)
                MayurReactionType.TURN_TO_TOUCH ->
                    drawSparkleBurst(w, h, 0.76f, 0.22f, Color(0xFF4DD0E1), fade * 0.85f, pose.reactionProgress)
            }
        } else if (pose.isDancing && !pose.isSleeping && quality != MayurAnimationQuality.BATTERY_SAVER) {
            val noteAlpha = (0.38f + pose.beatPulse * 0.52f).coerceIn(0.35f, 0.90f)
            drawFloatingMusicNotes(
                w = w,
                h = h,
                alpha = noteAlpha,
                progress = phase,
                count = if (quality == MayurAnimationQuality.HIGH) 2 else 1
            )
        }
    }

    private fun DrawScope.drawRainAndUmbrella(w: Float, h: Float, phase: Float, quality: MayurAnimationQuality) {
        val cx = w * 0.78f
        val cy = h * 0.14f
        val r = w * 0.14f
        drawLine(Color(0xFFFFCA28), Offset(cx, cy), Offset(cx + w * 0.03f, cy + h * 0.13f), w * 0.015f, StrokeCap.Round)
        val canopy = Path().apply {
            moveTo(cx - r, cy + r * 0.18f)
            quadraticTo(cx, cy - r * 0.92f, cx + r, cy + r * 0.18f)
            close()
        }
        drawPath(canopy, Color(0xFF0288D1))
        drawPath(canopy, Color(0xFFFFD54F), style = Stroke(width = w * 0.011f))

        val drops = quality.maxAccentParticles.coerceAtMost(4)
        for (i in 0 until drops) {
            val t = (phase + i * 0.27f) % 1f
            val dx = w * (0.14f + i * 0.24f)
            val dy = h * (0.06f + t * 0.26f)
            drawLine(
                color = Color(0xFF81D4FA).copy(alpha = (1f - t) * 0.72f),
                start = Offset(dx, dy),
                end = Offset(dx - w * 0.016f, dy + h * 0.045f),
                strokeWidth = w * 0.012f,
                cap = StrokeCap.Round
            )
        }
    }

    private fun DrawScope.drawSnowflakes(w: Float, h: Float, phase: Float, quality: MayurAnimationQuality) {
        val count = quality.maxAccentParticles.coerceAtMost(4)
        for (i in 0 until count) {
            val t = (phase + i * 0.29f) % 1f
            val x = w * (0.14f + i * 0.23f) + sin(t * 2f * PI.toFloat()) * w * 0.025f
            val y = h * (0.08f + t * 0.34f)
            drawCircle(Color(0xFFE1F5FE).copy(alpha = (1f - t) * 0.85f), w * 0.016f, Offset(x, y))
        }
    }

    private fun DrawScope.drawWindWisps(w: Float, h: Float, phase: Float) {
        val shift = sin(phase * 2f * PI.toFloat()) * w * 0.03f
        for (i in 0..1) {
            val y = h * (0.18f + i * 0.14f)
            val wisp = Path().apply {
                moveTo(w * 0.08f + shift, y)
                quadraticTo(w * 0.20f + shift, y - h * 0.04f, w * 0.32f + shift, y)
            }
            drawPath(wisp, Color(0xFF80DEEA).copy(alpha = 0.65f), style = Stroke(w * 0.012f, cap = StrokeCap.Round))
        }
    }

    private fun DrawScope.drawDiwaliDiya(w: Float, h: Float, timeSec: Float, quality: MayurAnimationQuality) {
        val cx = w * 0.84f
        val cy = h * 0.85f
        val flicker = 1f + sin(timeSec * 8f) * 0.14f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xAAFFCA28), Color.Transparent),
                center = Offset(cx, cy - h * 0.05f),
                radius = w * 0.095f * flicker
            ),
            radius = w * 0.095f * flicker,
            center = Offset(cx, cy - h * 0.05f)
        )
        val bowl = Path().apply {
            moveTo(cx - w * 0.07f, cy)
            quadraticTo(cx, cy + h * 0.07f, cx + w * 0.07f, cy)
            close()
        }
        drawPath(bowl, Color(0xFFD84315))
        drawPath(bowl, Color(0xFFFFD54F), style = Stroke(w * 0.01f))
        val flame = Path().apply {
            moveTo(cx, cy - h * 0.08f * flicker)
            quadraticTo(cx + w * 0.03f, cy - h * 0.02f, cx, cy)
            quadraticTo(cx - w * 0.03f, cy - h * 0.02f, cx, cy - h * 0.08f * flicker)
            close()
        }
        drawPath(flame, Color(0xFFFFB300))
        if (quality != MayurAnimationQuality.BATTERY_SAVER) {
            drawFourPointStar(Offset(w * 0.16f, h * 0.18f), w * 0.030f * flicker, Color(0xFFFFD54F))
        }
    }

    private fun DrawScope.drawHoliDots(w: Float, h: Float, timeSec: Float, quality: MayurAnimationQuality) {
        val palette = listOf(Color(0xFFEC407A), Color(0xFFFFCA28), Color(0xFF26C6DA), Color(0xFF66BB6A))
        val count = quality.maxAccentParticles.coerceAtMost(palette.size)
        for (i in 0 until count) {
            val a = timeSec * 1.4f + i * 1.6f
            val x = w * (0.16f + i * 0.22f) + cos(a) * w * 0.02f
            val y = h * (0.14f + (i % 2) * 0.08f) + sin(a) * h * 0.02f
            drawCircle(palette[i].copy(alpha = 0.8f), w * 0.019f, Offset(x, y))
        }
    }

    private fun DrawScope.drawKartikBoita(w: Float, h: Float, timeSec: Float) {
        val bob = sin(timeSec * 2.4f) * h * 0.012f
        val cx = w * 0.82f
        val cy = h * 0.86f + bob
        val hull = Path().apply {
            moveTo(cx - w * 0.09f, cy)
            quadraticTo(cx, cy + h * 0.07f, cx + w * 0.09f, cy)
            close()
        }
        drawPath(hull, Color(0xFFFFA000))
        val sail = Path().apply {
            moveTo(cx, cy - h * 0.09f)
            lineTo(cx + w * 0.05f, cy - h * 0.01f)
            lineTo(cx, cy - h * 0.01f)
            close()
        }
        drawPath(sail, Color(0xFFFFF3E0))
        drawCircle(Color(0xFFFFD54F), w * 0.018f, Offset(cx - w * 0.032f, cy - h * 0.018f))
    }

    private fun DrawScope.drawSleepingZzz(w: Float, h: Float, phase: Float) {
        for (i in 0..2) {
            val t = (phase + i * 0.33f) % 1f
            val alpha = (sin(t * PI.toFloat()) * 0.85f).coerceIn(0f, 0.85f)
            val x = w * (0.74f + i * 0.05f) + sin(t * 2f * PI.toFloat()) * w * 0.015f
            val y = h * (0.28f - t * 0.22f)
            val s = w * (0.030f + i * 0.008f)
            val zPath = Path().apply {
                moveTo(x - s * 0.5f, y - s * 0.5f)
                lineTo(x + s * 0.5f, y - s * 0.5f)
                lineTo(x - s * 0.5f, y + s * 0.5f)
                lineTo(x + s * 0.5f, y + s * 0.5f)
            }
            drawPath(
                zPath,
                Color(0xFFFFE082).copy(alpha = alpha),
                style = Stroke(w * 0.014f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }

    private fun DrawScope.drawSleepyGrump(w: Float, h: Float, irritated: Boolean, alpha: Float) {
        val cx = w * 0.83f
        val cy = h * 0.24f
        val s = w * 0.038f
        val color = (if (irritated) Color(0xFFFF8A65) else Color(0xFFFFE082)).copy(alpha = alpha)
        val stroke = Stroke(w * 0.014f, cap = StrokeCap.Round)
        drawArc(color, 10f, 80f, false, Offset(cx - s, cy - s), Size(s, s), style = stroke)
        drawArc(color, 190f, 80f, false, Offset(cx, cy), Size(s, s), style = stroke)
    }

    private fun DrawScope.drawSparkleBurst(
        w: Float,
        h: Float,
        cxFrac: Float,
        cyFrac: Float,
        color: Color,
        alpha: Float,
        progress: Float
    ) {
        val spread = progress * w * 0.08f
        drawFourPointStar(Offset(w * cxFrac - spread, h * cyFrac - spread * 0.4f), w * 0.036f, color.copy(alpha = alpha))
        drawFourPointStar(Offset(w * cxFrac + spread * 1.1f, h * cyFrac + spread * 0.2f), w * 0.028f, Color(0xFFFFF59D).copy(alpha = alpha))
    }

    private fun DrawScope.drawFloatingMusicNotes(
        w: Float,
        h: Float,
        alpha: Float,
        progress: Float,
        count: Int
    ) {
        val safeCount = count.coerceIn(1, 3)
        for (i in 0 until safeCount) {
            val t = (progress + i * 0.34f) % 1f
            val localAlpha = (sin(t * PI.toFloat()) * alpha).coerceIn(0f, 1f)
            val x = w * (if (i % 2 == 0) 0.16f else 0.84f) + sin(t * 2f * PI.toFloat()) * w * 0.024f
            val y = h * (0.34f - t * 0.24f)
            val noteColor = if (i % 2 == 0) Color(0xFFFFD54F) else Color(0xFF4DD0E1)
            val r = w * 0.020f
            drawCircle(noteColor.copy(alpha = localAlpha), r, Offset(x, y))
            drawLine(
                noteColor.copy(alpha = localAlpha),
                Offset(x + r * 0.85f, y),
                Offset(x + r * 0.85f, y - r * 3.1f),
                w * 0.011f,
                StrokeCap.Round
            )
            drawLine(
                noteColor.copy(alpha = localAlpha),
                Offset(x + r * 0.85f, y - r * 3.1f),
                Offset(x + r * 2.2f, y - r * 2.2f),
                w * 0.011f,
                StrokeCap.Round
            )
        }
    }

    private fun DrawScope.drawFourPointStar(center: Offset, radius: Float, color: Color) {
        val inner = radius * 0.28f
        val path = Path().apply {
            moveTo(center.x, center.y - radius)
            lineTo(center.x + inner, center.y - inner)
            lineTo(center.x + radius, center.y)
            lineTo(center.x + inner, center.y + inner)
            lineTo(center.x, center.y + radius)
            lineTo(center.x - inner, center.y + inner)
            lineTo(center.x - radius, center.y)
            lineTo(center.x - inner, center.y - inner)
            close()
        }
        drawPath(path, color)
    }
}
