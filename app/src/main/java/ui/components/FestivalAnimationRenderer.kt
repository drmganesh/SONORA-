package ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import domain.festival.FestivalEffectId
import domain.festival.FestivalRenderPlan
import ui.theme.EditorialColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fixed-capacity flat-array particle pool for SONORA's Festival Atmosphere Renderer.
 * Zero per-frame heap allocations during [step], keeping GC pressure at zero and CPU time sub-millisecond.
 * Strictly capped at <= 40 active particles across all festival effects.
 */
internal class FestivalParticlePool(
    val capacity: Int = 40,
    seed: Int = 1087
) {
    val safeCapacity: Int = capacity.coerceIn(1, 40)

    val x = FloatArray(safeCapacity)
    val y = FloatArray(safeCapacity)
    val vx = FloatArray(safeCapacity)
    val vy = FloatArray(safeCapacity)
    val size = FloatArray(safeCapacity)
    val phase = FloatArray(safeCapacity)
    val alpha = FloatArray(safeCapacity)
    val life = FloatArray(safeCapacity)
    val maxLife = FloatArray(safeCapacity)
    val colorSlot = IntArray(safeCapacity)
    val originX = FloatArray(safeCapacity)
    val originY = FloatArray(safeCapacity)

    var elapsedSec: Float = 0f
        private set
    private var activeEffect: FestivalEffectId = FestivalEffectId.NONE
    private var initialized: Boolean = false
    private val rng = Random(seed)

    fun configureIfNeeded(effectId: FestivalEffectId) {
        if (initialized && activeEffect == effectId) return
        activeEffect = effectId
        initialized = true
        elapsedSec = 0f
        for (i in 0 until safeCapacity) {
            seedParticle(i, effectId, initialSpread = true)
        }
    }

    private fun seedParticle(i: Int, effectId: FestivalEffectId, initialSpread: Boolean) {
        colorSlot[i] = i % 4
        phase[i] = rng.nextFloat() * (2f * PI.toFloat())
        when (effectId) {
            FestivalEffectId.DIWALI_FIREWORKS -> {
                // Cluster sparks into 3 gentle burst centers in the upper 45% of the screen
                val cluster = i % 3
                val cx = 0.22f + cluster * 0.28f + (rng.nextFloat() - 0.5f) * 0.08f
                val cy = 0.16f + (cluster % 2) * 0.14f + (rng.nextFloat() - 0.5f) * 0.05f
                originX[i] = cx
                originY[i] = cy
                val angle = ((i / 3) * 0.58f) + rng.nextFloat() * 0.35f
                val speed = 0.055f + rng.nextFloat() * 0.075f
                vx[i] = cos(angle) * speed
                vy[i] = sin(angle) * speed
                maxLife[i] = 2.4f + (cluster * 0.55f) + rng.nextFloat() * 0.6f
                life[i] = if (initialSpread) rng.nextFloat() * maxLife[i] else 0f
                x[i] = cx + vx[i] * life[i]
                y[i] = cy + vy[i] * life[i]
                size[i] = 2.4f + rng.nextFloat() * 2.8f
                alpha[i] = 0.72f
            }

            FestivalEffectId.KARTIK_BOITA_BANDANA,
            FestivalEffectId.RIVER_DIYA_GANGA -> {
                x[i] = if (initialSpread) rng.nextFloat() else -0.06f
                y[i] = 0.74f + rng.nextFloat() * 0.20f
                vx[i] = 0.014f + rng.nextFloat() * 0.018f
                vy[i] = 0f
                size[i] = 3.2f + rng.nextFloat() * 3.4f
                maxLife[i] = 10f
                life[i] = rng.nextFloat() * 10f
                alpha[i] = 0.68f + rng.nextFloat() * 0.25f
            }

            FestivalEffectId.DUSSEHRA_RAMA_ARROW -> {
                // Gentle rising ceremonial embers around the stylized flame vignette (lower right)
                val cx = 0.78f + (rng.nextFloat() - 0.5f) * 0.12f
                val cy = 0.78f + (rng.nextFloat() - 0.5f) * 0.08f
                originX[i] = cx
                originY[i] = cy
                x[i] = cx
                y[i] = if (initialSpread) cy - rng.nextFloat() * 0.22f else cy
                vx[i] = (rng.nextFloat() - 0.5f) * 0.022f
                vy[i] = -(0.042f + rng.nextFloat() * 0.055f)
                size[i] = 2.2f + rng.nextFloat() * 2.8f
                maxLife[i] = 2.6f + rng.nextFloat() * 1.4f
                life[i] = if (initialSpread) rng.nextFloat() * maxLife[i] else 0f
                alpha[i] = 0.72f
            }

            FestivalEffectId.HOLI_GULAL -> {
                x[i] = rng.nextFloat()
                y[i] = if (initialSpread) rng.nextFloat() else 1.05f
                vx[i] = (rng.nextFloat() - 0.5f) * 0.018f
                vy[i] = -(0.012f + rng.nextFloat() * 0.022f)
                size[i] = 18f + rng.nextFloat() * 34f
                maxLife[i] = 9f
                life[i] = rng.nextFloat() * 9f
                alpha[i] = 0.18f + rng.nextFloat() * 0.14f
            }

            FestivalEffectId.SANKRANTI_KITES -> {
                x[i] = if (initialSpread) rng.nextFloat() else -0.08f
                y[i] = 0.08f + rng.nextFloat() * 0.48f
                vx[i] = 0.016f + rng.nextFloat() * 0.022f
                vy[i] = -(0.004f + rng.nextFloat() * 0.009f)
                size[i] = 10f + rng.nextFloat() * 9f
                maxLife[i] = 12f
                life[i] = rng.nextFloat() * 12f
                alpha[i] = 0.65f + rng.nextFloat() * 0.20f
            }

            FestivalEffectId.PONGAL_KOLAM_STEAM -> {
                // Soft curling steam wisps rising in lower center/sides
                x[i] = 0.24f + rng.nextFloat() * 0.52f
                y[i] = if (initialSpread) 0.45f + rng.nextFloat() * 0.45f else 0.90f
                vx[i] = (rng.nextFloat() - 0.5f) * 0.01f
                vy[i] = -(0.024f + rng.nextFloat() * 0.026f)
                size[i] = 8f + rng.nextFloat() * 12f
                maxLife[i] = 6.5f
                life[i] = rng.nextFloat() * 6.5f
                alpha[i] = 0.26f + rng.nextFloat() * 0.18f
            }

            FestivalEffectId.SHIVARATRI_SACRED_NIGHT,
            FestivalEffectId.GOLDEN_AARTI_LAMPS -> {
                x[i] = rng.nextFloat()
                y[i] = if (initialSpread) rng.nextFloat() else 1.03f
                vx[i] = (rng.nextFloat() - 0.5f) * 0.010f
                vy[i] = -(0.014f + rng.nextFloat() * 0.022f)
                size[i] = 2.2f + rng.nextFloat() * 3.0f
                maxLife[i] = 7f
                life[i] = rng.nextFloat() * 7f
                alpha[i] = 0.55f + rng.nextFloat() * 0.35f
            }

            else -> {
                // Gentle falling petals / peacock feathers / floral motifs
                x[i] = rng.nextFloat()
                y[i] = if (initialSpread) rng.nextFloat() else -0.05f
                vx[i] = (rng.nextFloat() - 0.5f) * 0.016f
                vy[i] = 0.018f + rng.nextFloat() * 0.028f
                size[i] = 4.2f + rng.nextFloat() * 4.5f
                maxLife[i] = 8f
                life[i] = rng.nextFloat() * 8f
                alpha[i] = 0.60f + rng.nextFloat() * 0.28f
            }
        }
    }

    /**
     * Advances particle physics in-place with ZERO heap allocations.
     */
    fun step(deltaSec: Float, plan: FestivalRenderPlan) {
        if (!plan.isActive || plan.effectId == FestivalEffectId.NONE) return
        configureIfNeeded(plan.effectId)

        val clampedDt = deltaSec.coerceIn(0f, 0.06f)
        val speed = plan.speedMultiplier.coerceAtLeast(0f)
        val dt = clampedDt * speed
        if (dt <= 0f) return

        elapsedSec += dt
        val count = plan.particleLimit.coerceIn(0, safeCapacity)
        val effect = plan.effectId

        for (i in 0 until count) {
            when (effect) {
                FestivalEffectId.DIWALI_FIREWORKS -> {
                    life[i] += dt
                    if (life[i] >= maxLife[i]) {
                        life[i] = 0f
                        x[i] = originX[i]
                        y[i] = originY[i]
                    } else {
                        val t = life[i]
                        x[i] = originX[i] + vx[i] * t
                        y[i] = originY[i] + vy[i] * t + 0.016f * t * t
                    }
                }

                FestivalEffectId.DUSSEHRA_RAMA_ARROW -> {
                    life[i] += dt
                    if (life[i] >= maxLife[i]) {
                        life[i] = 0f
                        x[i] = originX[i]
                        y[i] = originY[i]
                    } else {
                        x[i] += vx[i] * dt + sin(elapsedSec * 2.2f + phase[i]) * 0.008f * dt
                        y[i] += vy[i] * dt
                    }
                }

                FestivalEffectId.KARTIK_BOITA_BANDANA,
                FestivalEffectId.RIVER_DIYA_GANGA -> {
                    x[i] += vx[i] * dt
                    if (x[i] > 1.08f) x[i] = -0.08f
                }

                FestivalEffectId.SANKRANTI_KITES -> {
                    x[i] += vx[i] * dt
                    y[i] += vy[i] * dt + sin(elapsedSec * 1.4f + phase[i]) * 0.012f * dt
                    if (x[i] > 1.10f) {
                        x[i] = -0.08f
                        y[i] = 0.14f + (i % 6) * 0.07f
                    }
                    if (y[i] < 0.04f) y[i] = 0.52f
                }

                FestivalEffectId.HOLI_GULAL,
                FestivalEffectId.PONGAL_KOLAM_STEAM,
                FestivalEffectId.SHIVARATRI_SACRED_NIGHT,
                FestivalEffectId.GOLDEN_AARTI_LAMPS -> {
                    x[i] += (vx[i] + sin(elapsedSec * 0.9f + phase[i]) * 0.012f) * dt
                    y[i] += vy[i] * dt
                    if (y[i] < -0.08f) {
                        y[i] = 1.06f
                        x[i] = ((x[i] + 0.37f) % 1f + 1f) % 1f
                    }
                    if (x[i] < -0.1f) x[i] = 1.08f
                    if (x[i] > 1.1f) x[i] = -0.08f
                }

                else -> {
                    x[i] += (vx[i] + sin(elapsedSec * 1.15f + phase[i]) * 0.018f) * dt
                    y[i] += vy[i] * dt
                    if (y[i] > 1.06f) {
                        y[i] = -0.05f
                        x[i] = ((x[i] + 0.29f) % 1f + 1f) % 1f
                    }
                    if (x[i] < -0.06f) x[i] = 1.04f
                    if (x[i] > 1.06f) x[i] = -0.04f
                }
            }
        }
    }
}

/**
 * Battery-efficient single-Canvas Festival Atmosphere overlay.
 * - Renders strictly behind UI controls (non-interactive).
 * - Pauses frame loop immediately when backgrounded, hidden, or in Reduce Motion mode.
 * - Respects theme light/dark contrast and album-art palette readability.
 */
@Composable
fun FestivalAnimationRenderer(
    plan: FestivalRenderPlan,
    colors: EditorialColors,
    screenVisible: Boolean = true,
    modifier: Modifier = Modifier,
    testTagName: String = "festival_atmosphere_overlay"
) {
    if (!plan.isActive || plan.effectId == FestivalEffectId.NONE) return

    val motionState = ui.motion.LocalEffectiveMotionState.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isLifecycleStarted by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isLifecycleStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val pool = remember { FestivalParticlePool(capacity = 40) }
    pool.configureIfNeeded(plan.effectId)

    val throttleAmbient = motionState.isPowerSaveMode ||
        motionState.isLowRamDevice ||
        motionState.effectiveEffectsLevel == domain.motion.MotionEffectsLevel.REDUCED_MOTION
    val effectiveMinFrameIntervalNs = if (throttleAmbient) {
        maxOf(plan.minFrameIntervalNanos, 33_333_333L) // cap to <= 30fps under Battery Saver / Reduced Motion
    } else {
        plan.minFrameIntervalNanos
    }

    var frameTick by remember { mutableLongStateOf(0L) }
    val shouldRunFrameLoop = screenVisible &&
        isLifecycleStarted &&
        motionState.ambientEffectsEnabled &&
        !plan.isStaticReducedMotion &&
        effectiveMinFrameIntervalNs != Long.MAX_VALUE

    LaunchedEffect(shouldRunFrameLoop, plan.effectId, effectiveMinFrameIntervalNs, plan.speedMultiplier, plan.particleLimit) {
        if (!shouldRunFrameLoop) return@LaunchedEffect
        var lastFrameNanos = 0L
        while (true) {
            withFrameNanos { nowNanos ->
                if (lastFrameNanos == 0L) {
                    lastFrameNanos = nowNanos
                } else {
                    val elapsedNanos = nowNanos - lastFrameNanos
                    if (elapsedNanos >= effectiveMinFrameIntervalNs) {
                        val dtSec = (elapsedNanos / 1_000_000_000f).coerceIn(0f, 0.06f)
                        pool.step(dtSec, plan)
                        lastFrameNanos = nowNanos
                        frameTick++
                    }
                }
            }
        }
    }

    // Reusable Path objects allocated once per composition (never inside onDraw)
    val reusablePath = remember { Path() }
    val reusableSecondaryPath = remember { Path() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag(testTagName)
    ) {
        // Read frameTick so Compose invalidates cleanly on throttled ticks
        @Suppress("UNUSED_VARIABLE")
        val tick = frameTick

        val w = size.width
        val h = size.height
        if (w <= 1f || h <= 1f) return@Canvas

        val isDark = colors.isDark
        val contrastFactor = if (isDark) 1.0f else 0.78f
        val baseAlpha = (plan.opacityScale * contrastFactor).coerceIn(0.14f, 0.76f)
        val count = plan.particleLimit.coerceIn(0, pool.safeCapacity)
        val t = pool.elapsedSec

        when (plan.effectId) {
            FestivalEffectId.DIWALI_FIREWORKS -> drawDiwaliAtmosphere(
                w = w,
                h = h,
                pool = pool,
                count = count,
                baseAlpha = baseAlpha,
                t = t,
                isDark = isDark
            )

            FestivalEffectId.KARTIK_BOITA_BANDANA -> drawKartikBoitaAtmosphere(
                w = w,
                h = h,
                pool = pool,
                count = count,
                baseAlpha = baseAlpha,
                t = t,
                isDark = isDark,
                boatPath = reusablePath,
                sailPath = reusableSecondaryPath
            )

            FestivalEffectId.DUSSEHRA_RAMA_ARROW -> drawDussehraAtmosphere(
                w = w,
                h = h,
                pool = pool,
                count = count,
                baseAlpha = baseAlpha,
                t = t,
                isDark = isDark,
                bowPath = reusablePath,
                flamePath = reusableSecondaryPath
            )

            FestivalEffectId.HOLI_GULAL -> drawHoliGulalAtmosphere(
                w = w,
                h = h,
                pool = pool,
                count = count,
                baseAlpha = baseAlpha,
                t = t
            )

            FestivalEffectId.GANESH_DIYA_FLOWERS -> drawGaneshAtmosphere(
                w = w,
                h = h,
                pool = pool,
                count = count,
                baseAlpha = baseAlpha,
                t = t,
                isDark = isDark
            )

            FestivalEffectId.DURGA_NAVRATRI_PETALS -> drawDurgaNavratriAtmosphere(
                w = w,
                h = h,
                pool = pool,
                count = count,
                baseAlpha = baseAlpha,
                t = t,
                isDark = isDark
            )

            FestivalEffectId.RATH_YATRA_CHARIOT -> drawRathYatraAtmosphere(
                w = w,
                h = h,
                pool = pool,
                count = count,
                baseAlpha = baseAlpha,
                t = t,
                isDark = isDark,
                chariotPath = reusablePath,
                flagPath = reusableSecondaryPath
            )

            FestivalEffectId.JANMASHTAMI_FLUTE_PEACOCK -> drawJanmashtamiAtmosphere(
                w = w,
                h = h,
                pool = pool,
                count = count,
                baseAlpha = baseAlpha,
                t = t,
                isDark = isDark
            )

            FestivalEffectId.SANKRANTI_KITES -> drawSankrantiKitesAtmosphere(
                w = w,
                h = h,
                pool = pool,
                count = count,
                baseAlpha = baseAlpha,
                t = t,
                kitePath = reusablePath
            )

            FestivalEffectId.PONGAL_KOLAM_STEAM -> drawPongalAtmosphere(
                w = w,
                h = h,
                pool = pool,
                count = count,
                baseAlpha = baseAlpha,
                t = t,
                isDark = isDark
            )

            FestivalEffectId.ONAM_POOKALAM_BOAT -> drawOnamAtmosphere(
                w = w,
                h = h,
                pool = pool,
                count = count,
                baseAlpha = baseAlpha,
                t = t,
                isDark = isDark,
                boatPath = reusablePath
            )

            FestivalEffectId.BIHU_FESTIVE_PATTERN -> drawBihuAtmosphere(
                w = w,
                h = h,
                pool = pool,
                count = count,
                baseAlpha = baseAlpha,
                t = t,
                isDark = isDark,
                motifPath = reusablePath
            )

            FestivalEffectId.SHIVARATRI_SACRED_NIGHT -> drawShivaratriAtmosphere(
                w = w,
                h = h,
                pool = pool,
                count = count,
                baseAlpha = baseAlpha,
                t = t,
                isDark = isDark
            )

            FestivalEffectId.SPRING_BLOOM_PETALS -> drawSpringBloomAtmosphere(
                w = w,
                h = h,
                pool = pool,
                count = count,
                baseAlpha = baseAlpha,
                t = t
            )

            FestivalEffectId.RIVER_DIYA_GANGA -> drawRiverDiyaAtmosphere(
                w = w,
                h = h,
                pool = pool,
                count = count,
                baseAlpha = baseAlpha,
                t = t,
                isDark = isDark
            )

            FestivalEffectId.GOLDEN_AARTI_LAMPS -> drawGoldenAartiAtmosphere(
                w = w,
                h = h,
                pool = pool,
                count = count,
                baseAlpha = baseAlpha,
                t = t,
                isDark = isDark
            )

            FestivalEffectId.NONE -> Unit
        }
    }
}

private val DIWALI_COLORS = arrayOf(
    Color(0xFFFFD54F),
    Color(0xFFFFAB40),
    Color(0xFFFF8A65),
    Color(0xFFFFF176)
)

private fun DrawScope.drawDiwaliAtmosphere(
    w: Float,
    h: Float,
    pool: FestivalParticlePool,
    count: Int,
    baseAlpha: Float,
    t: Float,
    isDark: Boolean
) {
    // Soft warm golden glow at top edges
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFB74D).copy(alpha = baseAlpha * (if (isDark) 0.16f else 0.10f)),
                Color.Transparent
            ),
            center = Offset(w * 0.5f, h * 0.18f),
            radius = w * 0.72f
        )
    )

    // Gentle bursting firework sparks in upper sky
    for (i in 0 until count) {
        val progress = (pool.life[i] / pool.maxLife[i]).coerceIn(0f, 1f)
        val fade = sin(progress * PI.toFloat()).coerceIn(0f, 1f)
        val px = pool.x[i] * w
        val py = pool.y[i] * h
        val color = DIWALI_COLORS[pool.colorSlot[i] % DIWALI_COLORS.size]
        val sparkAlpha = baseAlpha * fade * 0.92f
        if (sparkAlpha > 0.02f) {
            val ox = pool.originX[i] * w
            val oy = pool.originY[i] * h
            // Subtle trailing ray from burst origin
            drawLine(
                color = color.copy(alpha = sparkAlpha * 0.32f),
                start = Offset(px - (px - ox) * 0.28f, py - (py - oy) * 0.28f),
                end = Offset(px, py),
                strokeWidth = 1.4.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(
                color = color.copy(alpha = sparkAlpha),
                radius = pool.size[i].dp.toPx() * (0.65f + 0.45f * (1f - progress)),
                center = Offset(px, py)
            )
        }
    }

    // Two delicate edge diyas at bottom left & right
    val flicker = 0.88f + 0.12f * sin(t * 5.2f)
    drawMinimalDiya(Offset(w * 0.12f, h * 0.91f), 11.dp.toPx(), baseAlpha * flicker)
    drawMinimalDiya(Offset(w * 0.88f, h * 0.91f), 11.dp.toPx(), baseAlpha * (0.94f + 0.06f * cos(t * 4.7f)))
}

private fun DrawScope.drawKartikBoitaAtmosphere(
    w: Float,
    h: Float,
    pool: FestivalParticlePool,
    count: Int,
    baseAlpha: Float,
    t: Float,
    isDark: Boolean,
    boatPath: Path,
    sailPath: Path
) {
    // Calm river horizon glow in bottom 26%
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color(0xFF1565C0).copy(alpha = baseAlpha * (if (isDark) 0.18f else 0.10f)),
                Color(0xFF0D47A1).copy(alpha = baseAlpha * (if (isDark) 0.24f else 0.14f))
            ),
            startY = h * 0.68f,
            endY = h
        )
    )

    // Subtle horizontal water shimmer lines
    val shimmerColor = if (isDark) Color(0xFF90CAF9) else Color(0xFF1976D2)
    for (line in 0 until 4) {
        val ly = h * (0.76f + line * 0.055f)
        val drift = sin(t * 0.7f + line * 1.3f) * w * 0.04f
        drawLine(
            color = shimmerColor.copy(alpha = baseAlpha * 0.22f),
            start = Offset(w * 0.08f + drift, ly),
            end = Offset(w * 0.92f + drift, ly),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round
        )
    }

    // 2 graceful stylized Odisha Boita boats gliding gently across the lower water
    for (b in 0 until 2) {
        val speed = if (b == 0) 0.024f else 0.017f
        val bx = (((t * speed + b * 0.52f) % 1.24f) - 0.12f) * w
        val bob = sin(t * 1.6f + b * 2.1f) * 3.dp.toPx()
        val by = h * (if (b == 0) 0.84f else 0.79f) + bob
        val scale = if (b == 0) 1.0f else 0.76f
        val halfW = 24.dp.toPx() * scale
        val hullH = 9.dp.toPx() * scale

        boatPath.reset()
        boatPath.moveTo(bx - halfW, by - hullH * 0.35f)
        boatPath.quadraticTo(bx, by + hullH * 1.15f, bx + halfW, by - hullH * 0.35f)
        boatPath.quadraticTo(bx, by + hullH * 0.25f, bx - halfW, by - hullH * 0.35f)
        boatPath.close()

        drawPath(
            path = boatPath,
            color = Color(0xFFD84315).copy(alpha = baseAlpha * 0.78f)
        )

        // Traditional triangular Boita sail
        sailPath.reset()
        sailPath.moveTo(bx - halfW * 0.15f, by - hullH * 0.2f)
        sailPath.lineTo(bx + halfW * 0.05f, by - hullH * 2.5f)
        sailPath.lineTo(bx + halfW * 0.65f, by - hullH * 0.45f)
        sailPath.close()
        drawPath(
            path = sailPath,
            color = Color(0xFFFFECB3).copy(alpha = baseAlpha * 0.72f)
        )

        // Glowing lamp inside the boita + soft water reflection
        drawCircle(
            color = Color(0xFFFFD54F).copy(alpha = baseAlpha * 0.38f),
            radius = 12.dp.toPx() * scale,
            center = Offset(bx - halfW * 0.35f, by - hullH * 0.45f)
        )
        drawCircle(
            color = Color(0xFFFFF59D).copy(alpha = baseAlpha * 0.95f),
            radius = 3.2.dp.toPx() * scale,
            center = Offset(bx - halfW * 0.35f, by - hullH * 0.45f)
        )
        // Golden reflection below boat
        drawOval(
            color = Color(0xFFFFCA28).copy(alpha = baseAlpha * 0.20f),
            topLeft = Offset(bx - halfW * 0.6f, by + hullH * 0.7f),
            size = Size(halfW * 1.2f, 4.dp.toPx() * scale)
        )
    }

    // Floating lit diyas & golden reflections
    val diyaCount = count.coerceAtMost(14)
    for (i in 0 until diyaCount) {
        val px = pool.x[i] * w
        val py = pool.y[i] * h + sin(t * 1.8f + pool.phase[i]) * 2.dp.toPx()
        val flicker = 0.80f + 0.20f * sin(t * 4.5f + pool.phase[i])
        drawMinimalDiya(Offset(px, py), pool.size[i].dp.toPx(), baseAlpha * flicker * 0.85f)
    }
}

private fun DrawScope.drawDussehraAtmosphere(
    w: Float,
    h: Float,
    pool: FestivalParticlePool,
    count: Int,
    baseAlpha: Float,
    t: Float,
    isDark: Boolean,
    bowPath: Path,
    flamePath: Path
) {
    // Cycle: 0..2.2s draw bow & luminous arrow flight; 2.2..6.0s ceremonial saffron flame glow
    val cycle = t % 6.0f
    val arrowFlight = (cycle / 1.8f).coerceIn(0f, 1f)
    val ignition = if (cycle >= 1.6f) ((cycle - 1.6f) / 0.8f).coerceIn(0f, 1f) else 0f
    val flamePulse = 0.85f + 0.15f * sin(t * 4.4f)

    // Warm ceremonial saffron backdrop glow in lower right
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFF6D00).copy(alpha = baseAlpha * (0.10f + 0.16f * ignition) * (if (isDark) 1f else 0.72f)),
                Color.Transparent
            ),
            center = Offset(w * 0.78f, h * 0.80f),
            radius = w * 0.48f
        )
    )

    // Left corner: dignified minimalist silhouette of Lord Rama's golden bow arc
    val bowCenterX = w * 0.16f
    val bowCenterY = h * 0.82f
    val bowRadius = 26.dp.toPx()
    bowPath.reset()
    bowPath.moveTo(bowCenterX - bowRadius * 0.3f, bowCenterY - bowRadius)
    bowPath.quadraticTo(bowCenterX + bowRadius * 0.85f, bowCenterY, bowCenterX - bowRadius * 0.3f, bowCenterY + bowRadius)
    drawPath(
        path = bowPath,
        color = Color(0xFFFFCA28).copy(alpha = baseAlpha * 0.78f),
        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
    )
    // Bowstring
    drawLine(
        color = Color(0xFFFFE082).copy(alpha = baseAlpha * 0.45f),
        start = Offset(bowCenterX - bowRadius * 0.3f, bowCenterY - bowRadius),
        end = Offset(bowCenterX - bowRadius * 0.3f, bowCenterY + bowRadius),
        strokeWidth = 1.dp.toPx()
    )

    // Right corner: stylized ceremonial ten-crown silhouette (minimalist architectural motif)
    val effigyX = w * 0.78f
    val effigyBaseY = h * 0.88f
    val silhouetteColor = (if (isDark) Color(0xFFFFAB40) else Color(0xFFBF360C)).copy(alpha = baseAlpha * 0.42f)
    drawRoundRect(
        color = silhouetteColor,
        topLeft = Offset(effigyX - 11.dp.toPx(), effigyBaseY - 24.dp.toPx()),
        size = Size(22.dp.toPx(), 24.dp.toPx()),
        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
    )
    // Stylized crown arcs
    for (c in -2..2) {
        drawCircle(
            color = silhouetteColor,
            radius = if (c == 0) 6.dp.toPx() else 3.8.dp.toPx(),
            center = Offset(effigyX + c * 6.5.dp.toPx(), effigyBaseY - 28.dp.toPx())
        )
    }

    // Luminous arrow streak travelling gracefully from bow toward ceremonial flame
    if (cycle < 2.2f) {
        val sx = bowCenterX + bowRadius * 0.2f
        val sy = bowCenterY - 2.dp.toPx()
        val ex = effigyX
        val ey = effigyBaseY - 18.dp.toPx()
        val headX = sx + (ex - sx) * arrowFlight
        val headY = sy + (ey - sy) * arrowFlight - sin(arrowFlight * PI.toFloat()) * 32.dp.toPx()
        val tailFrac = (arrowFlight - 0.22f).coerceAtLeast(0f)
        val tailX = sx + (ex - sx) * tailFrac
        val tailY = sy + (ey - sy) * tailFrac - sin(tailFrac * PI.toFloat()) * 32.dp.toPx()

        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, Color(0xFFFFD54F).copy(alpha = baseAlpha * 0.95f)),
                start = Offset(tailX, tailY),
                end = Offset(headX, headY)
            ),
            start = Offset(tailX, tailY),
            end = Offset(headX, headY),
            strokeWidth = 2.6.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(
            color = Color(0xFFFFF59D).copy(alpha = baseAlpha),
            radius = 3.5.dp.toPx(),
            center = Offset(headX, headY)
        )
    }

    // Controlled golden-saffron ceremonial flame upon ignition (subtle & respectful)
    if (ignition > 0.05f) {
        val flameH = 28.dp.toPx() * ignition * flamePulse
        val flameW = 14.dp.toPx() * ignition
        flamePath.reset()
        flamePath.moveTo(effigyX, effigyBaseY - 20.dp.toPx() - flameH)
        flamePath.quadraticTo(effigyX + flameW, effigyBaseY - 16.dp.toPx(), effigyX, effigyBaseY - 6.dp.toPx())
        flamePath.quadraticTo(effigyX - flameW, effigyBaseY - 16.dp.toPx(), effigyX, effigyBaseY - 20.dp.toPx() - flameH)
        flamePath.close()
        drawPath(
            path = flamePath,
            color = Color(0xFFFF8F00).copy(alpha = baseAlpha * 0.68f * ignition)
        )
    }

    // Rising soft golden embers
    for (i in 0 until count) {
        val progress = (pool.life[i] / pool.maxLife[i]).coerceIn(0f, 1f)
        val fade = sin(progress * PI.toFloat()).coerceIn(0f, 1f)
        val color = if (i % 2 == 0) Color(0xFFFFCA28) else Color(0xFFFF7043)
        drawCircle(
            color = color.copy(alpha = baseAlpha * fade * (0.45f + 0.55f * ignition)),
            radius = pool.size[i].dp.toPx() * 0.72f,
            center = Offset(pool.x[i] * w, pool.y[i] * h)
        )
    }
}

private val HOLI_COLORS = arrayOf(
    Color(0xFFEC407A),
    Color(0xFFFFA726),
    Color(0xFF26C6DA),
    Color(0xFFAB47BC)
)

private fun DrawScope.drawHoliGulalAtmosphere(
    w: Float,
    h: Float,
    pool: FestivalParticlePool,
    count: Int,
    baseAlpha: Float,
    t: Float
) {
    // Soft drifting translucent gulal clouds
    for (i in 0 until count) {
        val px = pool.x[i] * w
        val py = pool.y[i] * h
        val baseColor = HOLI_COLORS[pool.colorSlot[i] % HOLI_COLORS.size]
        val radius = pool.size[i].dp.toPx() * (0.9f + 0.12f * sin(t * 0.9f + pool.phase[i]))
        val cloudAlpha = (baseAlpha * pool.alpha[i] * 1.45f).coerceIn(0.05f, 0.28f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(baseColor.copy(alpha = cloudAlpha), Color.Transparent),
                center = Offset(px, py),
                radius = radius
            ),
            radius = radius,
            center = Offset(px, py)
        )
        // Small gentle powder speck at center
        drawCircle(
            color = baseColor.copy(alpha = (baseAlpha * 0.55f).coerceAtMost(0.45f)),
            radius = 2.dp.toPx(),
            center = Offset(px + radius * 0.2f, py - radius * 0.15f)
        )
    }
}

private val MARIGOLD_COLORS = arrayOf(
    Color(0xFFFFA000),
    Color(0xFFFFCA28),
    Color(0xFFFF7043),
    Color(0xFFF48FB1)
)

private fun DrawScope.drawGaneshAtmosphere(
    w: Float,
    h: Float,
    pool: FestivalParticlePool,
    count: Int,
    baseAlpha: Float,
    t: Float,
    isDark: Boolean
) {
    // Warm marigold halo at top
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFB300).copy(alpha = baseAlpha * (if (isDark) 0.14f else 0.09f)),
                Color.Transparent
            ),
            center = Offset(w * 0.5f, h * 0.14f),
            radius = w * 0.65f
        )
    )
    drawFloatingPetals(w, h, pool, count, baseAlpha, t, MARIGOLD_COLORS)
    val flicker = 0.88f + 0.12f * sin(t * 4.2f)
    drawMinimalDiya(Offset(w * 0.14f, h * 0.90f), 10.dp.toPx(), baseAlpha * flicker)
    drawMinimalDiya(Offset(w * 0.86f, h * 0.90f), 10.dp.toPx(), baseAlpha * flicker)
}

private val DURGA_COLORS = arrayOf(
    Color(0xFFE53935),
    Color(0xFFFFB300),
    Color(0xFFFF7043),
    Color(0xFFFFD54F)
)

private fun DrawScope.drawDurgaNavratriAtmosphere(
    w: Float,
    h: Float,
    pool: FestivalParticlePool,
    count: Int,
    baseAlpha: Float,
    t: Float,
    isDark: Boolean
) {
    // Rhythmic warm dhunuchi / festive glow pulse
    val pulse = 0.78f + 0.22f * sin(t * 1.8f)
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFE53935).copy(alpha = baseAlpha * 0.12f * pulse * (if (isDark) 1f else 0.7f)),
                Color(0xFFFFA000).copy(alpha = baseAlpha * 0.08f * pulse),
                Color.Transparent
            ),
            center = Offset(w * 0.5f, h * 0.85f),
            radius = w * 0.70f
        )
    )
    // Soft translucent dhunuchi smoke wisps rising from bottom
    val smokeColor = if (isDark) Color(0xFFFFE0B2) else Color(0xFFBCAAA4)
    for (s in 0 until 3) {
        val sx = w * (0.28f + s * 0.22f) + sin(t * 0.8f + s * 1.9f) * 14.dp.toPx()
        val sy = h * (0.86f - ((t * 0.08f + s * 0.33f) % 0.32f))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(smokeColor.copy(alpha = baseAlpha * 0.14f), Color.Transparent),
                center = Offset(sx, sy),
                radius = 36.dp.toPx()
            ),
            radius = 36.dp.toPx(),
            center = Offset(sx, sy)
        )
    }
    drawFloatingPetals(w, h, pool, count, baseAlpha, t, DURGA_COLORS)
}

private fun DrawScope.drawRathYatraAtmosphere(
    w: Float,
    h: Float,
    pool: FestivalParticlePool,
    count: Int,
    baseAlpha: Float,
    t: Float,
    isDark: Boolean,
    chariotPath: Path,
    flagPath: Path
) {
    // Subtle stylized temple chariot silhouette gliding slowly across bottom background
    val cx = (((t * 0.018f + 0.35f) % 1.24f) - 0.12f) * w
    val baseY = h * 0.90f
    val chariotW = 54.dp.toPx()
    val wheelR = 9.dp.toPx()
    val bodyColor = Color(0xFFD32F2F).copy(alpha = baseAlpha * (if (isDark) 0.52f else 0.38f))
    val goldColor = Color(0xFFFFCA28).copy(alpha = baseAlpha * (if (isDark) 0.68f else 0.50f))

    // Stepped Nandighosa temple spire silhouette
    chariotPath.reset()
    chariotPath.moveTo(cx - chariotW * 0.5f, baseY - wheelR)
    chariotPath.lineTo(cx - chariotW * 0.38f, baseY - wheelR - 22.dp.toPx())
    chariotPath.lineTo(cx - chariotW * 0.24f, baseY - wheelR - 36.dp.toPx())
    chariotPath.lineTo(cx, baseY - wheelR - 50.dp.toPx())
    chariotPath.lineTo(cx + chariotW * 0.24f, baseY - wheelR - 36.dp.toPx())
    chariotPath.lineTo(cx + chariotW * 0.38f, baseY - wheelR - 22.dp.toPx())
    chariotPath.lineTo(cx + chariotW * 0.5f, baseY - wheelR)
    chariotPath.close()
    drawPath(chariotPath, color = bodyColor)
    drawPath(chariotPath, color = goldColor, style = Stroke(width = 1.5.dp.toPx()))

    // Fluttering traditional red-gold temple flag at apex
    val flutter = sin(t * 4.2f) * 3.dp.toPx()
    val topY = baseY - wheelR - 50.dp.toPx()
    flagPath.reset()
    flagPath.moveTo(cx, topY)
    flagPath.lineTo(cx, topY - 12.dp.toPx())
    flagPath.lineTo(cx + 18.dp.toPx(), topY - 6.dp.toPx() + flutter)
    flagPath.close()
    drawPath(flagPath, color = goldColor)

    // Chariot wheels with subtle spoke rotation
    for (wIdx in -1..1) {
        val wx = cx + wIdx * 17.dp.toPx()
        val wy = baseY - wheelR * 0.6f
        drawCircle(color = goldColor, radius = wheelR * 0.72f, center = Offset(wx, wy), style = Stroke(width = 1.6.dp.toPx()))
    }

    drawFloatingPetals(w, h, pool, count, baseAlpha, t, MARIGOLD_COLORS)
}

private val PEACOCK_COLORS = arrayOf(
    Color(0xFF26A69A),
    Color(0xFF42A5F5),
    Color(0xFFFFD54F),
    Color(0xFF7E57C2)
)

private fun DrawScope.drawJanmashtamiAtmosphere(
    w: Float,
    h: Float,
    pool: FestivalParticlePool,
    count: Int,
    baseAlpha: Float,
    t: Float,
    isDark: Boolean
) {
    // Tranquil blue-gold aura
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF1E88E5).copy(alpha = baseAlpha * (if (isDark) 0.16f else 0.09f)),
                Color.Transparent
            ),
            center = Offset(w * 0.78f, h * 0.22f),
            radius = w * 0.62f
        )
    )

    // Minimal diagonal golden flute motif in top-right corner
    val fx1 = w * 0.70f
    val fy1 = h * 0.14f
    val fx2 = w * 0.90f
    val fy2 = h * 0.20f
    drawLine(
        color = Color(0xFFFFD54F).copy(alpha = baseAlpha * 0.56f),
        start = Offset(fx1, fy1),
        end = Offset(fx2, fy2),
        strokeWidth = 3.5.dp.toPx(),
        cap = StrokeCap.Round
    )
    for (hole in 1..4) {
        val frac = hole * 0.18f + 0.12f
        drawCircle(
            color = Color(0xFF0D47A1).copy(alpha = baseAlpha * 0.65f),
            radius = 1.1.dp.toPx(),
            center = Offset(fx1 + (fx2 - fx1) * frac, fy1 + (fy2 - fy1) * frac)
        )
    }

    // Floating peacock-feather eye motifs & blue-gold sparkles
    for (i in 0 until count) {
        val px = pool.x[i] * w
        val py = pool.y[i] * h
        val r = pool.size[i].dp.toPx()
        if (i % 3 == 0) {
            // Stylized concentric peacock feather eye
            drawOval(
                color = Color(0xFF26A69A).copy(alpha = baseAlpha * 0.45f),
                topLeft = Offset(px - r, py - r * 1.35f),
                size = Size(r * 2f, r * 2.7f)
            )
            drawCircle(
                color = Color(0xFF1565C0).copy(alpha = baseAlpha * 0.62f),
                radius = r * 0.58f,
                center = Offset(px, py)
            )
            drawCircle(
                color = Color(0xFFFFD54F).copy(alpha = baseAlpha * 0.78f),
                radius = r * 0.28f,
                center = Offset(px, py)
            )
        } else {
            val c = PEACOCK_COLORS[pool.colorSlot[i] % PEACOCK_COLORS.size]
            val twinkle = 0.65f + 0.35f * sin(t * 2.6f + pool.phase[i])
            drawCircle(
                color = c.copy(alpha = baseAlpha * twinkle * 0.78f),
                radius = r * 0.42f,
                center = Offset(px, py)
            )
        }
    }
}

private val KITE_COLORS = arrayOf(
    Color(0xFFFF7043),
    Color(0xFF42A5F5),
    Color(0xFFFFCA28),
    Color(0xFFEC407A)
)

private fun DrawScope.drawSankrantiKitesAtmosphere(
    w: Float,
    h: Float,
    pool: FestivalParticlePool,
    count: Int,
    baseAlpha: Float,
    t: Float,
    kitePath: Path
) {
    val kiteCount = count.coerceAtMost(12)
    for (i in 0 until kiteCount) {
        val px = pool.x[i] * w
        val py = pool.y[i] * h
        val sz = pool.size[i].dp.toPx()
        val color = KITE_COLORS[pool.colorSlot[i] % KITE_COLORS.size]
        val tilt = -18f + sin(t * 1.3f + pool.phase[i]) * 10f

        rotate(degrees = tilt, pivot = Offset(px, py)) {
            kitePath.reset()
            kitePath.moveTo(px, py - sz)
            kitePath.lineTo(px + sz * 0.78f, py)
            kitePath.lineTo(px, py + sz * 1.15f)
            kitePath.lineTo(px - sz * 0.78f, py)
            kitePath.close()
            drawPath(kitePath, color = color.copy(alpha = baseAlpha * 0.66f))
            // Delicate kite tail
            val wave = sin(t * 3.0f + pool.phase[i]) * 5.dp.toPx()
            drawLine(
                color = color.copy(alpha = baseAlpha * 0.42f),
                start = Offset(px, py + sz * 1.15f),
                end = Offset(px + wave, py + sz * 2.4f),
                strokeWidth = 1.2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

private fun DrawScope.drawPongalAtmosphere(
    w: Float,
    h: Float,
    pool: FestivalParticlePool,
    count: Int,
    baseAlpha: Float,
    t: Float,
    isDark: Boolean
) {
    val kolamColor = (if (isDark) Color(0xFFFFF8E1) else Color(0xFFEF6C00)).copy(alpha = baseAlpha * 0.28f)
    val cx = w * 0.5f
    val cy = h * 0.88f
    val ringR = 46.dp.toPx()
    // Geometric kolam dot ring at bottom center
    drawCircle(color = kolamColor, radius = ringR, center = Offset(cx, cy), style = Stroke(width = 1.2.dp.toPx()))
    drawCircle(color = kolamColor, radius = ringR * 0.58f, center = Offset(cx, cy), style = Stroke(width = 1.dp.toPx()))
    for (p in 0 until 8) {
        val a = (p * PI / 4.0).toFloat()
        drawCircle(
            color = kolamColor,
            radius = 2.6.dp.toPx(),
            center = Offset(cx + cos(a) * ringR * 0.78f, cy + sin(a) * ringR * 0.78f)
        )
    }

    // Slender sugarcane accents along lower edges
    val caneColor = Color(0xFF66BB6A).copy(alpha = baseAlpha * 0.40f)
    drawLine(caneColor, Offset(w * 0.08f, h * 0.96f), Offset(w * 0.12f, h * 0.74f), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
    drawLine(caneColor, Offset(w * 0.92f, h * 0.96f), Offset(w * 0.88f, h * 0.74f), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)

    // Slow curling warm harvest steam wisps
    val steamColor = if (isDark) Color(0xFFFFF3E0) else Color(0xFFFFB74D)
    for (i in 0 until count) {
        val px = pool.x[i] * w + sin(t * 1.1f + pool.phase[i]) * 8.dp.toPx()
        val py = pool.y[i] * h
        val r = pool.size[i].dp.toPx()
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(steamColor.copy(alpha = baseAlpha * pool.alpha[i] * 0.95f), Color.Transparent),
                center = Offset(px, py),
                radius = r
            ),
            radius = r,
            center = Offset(px, py)
        )
    }
}

private val ONAM_COLORS = arrayOf(
    Color(0xFFFFCA28),
    Color(0xFFFF7043),
    Color(0xFFEC407A),
    Color(0xFF66BB6A)
)

private fun DrawScope.drawOnamAtmosphere(
    w: Float,
    h: Float,
    pool: FestivalParticlePool,
    count: Int,
    baseAlpha: Float,
    t: Float,
    isDark: Boolean,
    boatPath: Path
) {
    // Subtle concentric Pookalam floral ring motif at bottom center
    val cx = w * 0.5f
    val cy = h * 0.88f
    for (ring in 0 until 3) {
        val r = (24 + ring * 13).dp.toPx()
        drawCircle(
            color = ONAM_COLORS[ring].copy(alpha = baseAlpha * (if (isDark) 0.24f else 0.18f)),
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = 2.4.dp.toPx())
        )
    }

    // Gentle Chundan Vallam curved snake-boat accent along bottom horizon
    val bx = (((t * 0.02f + 0.2f) % 1.24f) - 0.12f) * w
    val by = h * 0.92f
    boatPath.reset()
    boatPath.moveTo(bx - 30.dp.toPx(), by - 3.dp.toPx())
    boatPath.quadraticTo(bx, by + 6.dp.toPx(), bx + 30.dp.toPx(), by - 10.dp.toPx())
    drawPath(
        path = boatPath,
        color = Color(0xFF8D6E63).copy(alpha = baseAlpha * 0.55f),
        style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
    )

    drawFloatingPetals(w, h, pool, count, baseAlpha, t, ONAM_COLORS)
}

private val BIHU_COLORS = arrayOf(
    Color(0xFFD32F2F),
    Color(0xFFF5F5F5),
    Color(0xFFEC407A),
    Color(0xFFFFB74D)
)

private fun DrawScope.drawBihuAtmosphere(
    w: Float,
    h: Float,
    pool: FestivalParticlePool,
    count: Int,
    baseAlpha: Float,
    t: Float,
    isDark: Boolean,
    motifPath: Path
) {
    // Subtle traditional Assamese Gamusa diamond woven border accent near bottom edge
    val bandY = h * 0.93f
    val step = 28.dp.toPx()
    val red = Color(0xFFD32F2F).copy(alpha = baseAlpha * (if (isDark) 0.34f else 0.24f))
    var xPos = 16.dp.toPx()
    while (xPos < w - 16.dp.toPx()) {
        val d = 6.dp.toPx()
        motifPath.reset()
        motifPath.moveTo(xPos, bandY - d)
        motifPath.lineTo(xPos + d, bandY)
        motifPath.lineTo(xPos, bandY + d)
        motifPath.lineTo(xPos - d, bandY)
        motifPath.close()
        drawPath(motifPath, color = red)
        xPos += step
    }
    drawFloatingPetals(w, h, pool, count, baseAlpha, t, BIHU_COLORS)
}

private fun DrawScope.drawShivaratriAtmosphere(
    w: Float,
    h: Float,
    pool: FestivalParticlePool,
    count: Int,
    baseAlpha: Float,
    t: Float,
    isDark: Boolean
) {
    // Serene silver-indigo sacred night glow
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF5C6BC0).copy(alpha = baseAlpha * (if (isDark) 0.20f else 0.10f)),
                Color.Transparent
            ),
            center = Offset(w * 0.80f, h * 0.16f),
            radius = w * 0.56f
        )
    )
    // Minimal silver crescent moon silhouette
    val moonCenter = Offset(w * 0.82f, h * 0.14f)
    val moonR = 13.dp.toPx()
    val moonColor = (if (isDark) Color(0xFFE8EAF6) else Color(0xFF3949AB)).copy(alpha = baseAlpha * 0.65f)
    drawArc(
        color = moonColor,
        startAngle = 35f,
        sweepAngle = 210f,
        useCenter = false,
        topLeft = Offset(moonCenter.x - moonR, moonCenter.y - moonR),
        size = Size(moonR * 2f, moonR * 2f),
        style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
    )
    // Calm drifting sacred silver sparkles
    for (i in 0 until count) {
        val twinkle = 0.50f + 0.50f * sin(t * 2.2f + pool.phase[i])
        val c = if (i % 2 == 0) Color(0xFFE8EAF6) else Color(0xFF9FA8DA)
        drawCircle(
            color = c.copy(alpha = baseAlpha * twinkle * 0.72f),
            radius = pool.size[i].dp.toPx() * 0.68f,
            center = Offset(pool.x[i] * w, pool.y[i] * h)
        )
    }
}

private val SPRING_COLORS = arrayOf(
    Color(0xFFFFD54F),
    Color(0xFFFFAB91),
    Color(0xFFF48FB1),
    Color(0xFFA5D6A7)
)

private fun DrawScope.drawSpringBloomAtmosphere(
    w: Float,
    h: Float,
    pool: FestivalParticlePool,
    count: Int,
    baseAlpha: Float,
    t: Float
) {
    drawFloatingPetals(w, h, pool, count, baseAlpha, t, SPRING_COLORS)
}

private fun DrawScope.drawRiverDiyaAtmosphere(
    w: Float,
    h: Float,
    pool: FestivalParticlePool,
    count: Int,
    baseAlpha: Float,
    t: Float,
    isDark: Boolean
) {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color(0xFF0277BD).copy(alpha = baseAlpha * (if (isDark) 0.16f else 0.09f))
            ),
            startY = h * 0.70f,
            endY = h
        )
    )
    val diyaCount = count.coerceAtMost(14)
    for (i in 0 until diyaCount) {
        val px = pool.x[i] * w
        val py = pool.y[i] * h + sin(t * 1.5f + pool.phase[i]) * 2.dp.toPx()
        val flicker = 0.82f + 0.18f * sin(t * 4.2f + pool.phase[i])
        drawMinimalDiya(Offset(px, py), pool.size[i].dp.toPx(), baseAlpha * flicker)
    }
}

private fun DrawScope.drawGoldenAartiAtmosphere(
    w: Float,
    h: Float,
    pool: FestivalParticlePool,
    count: Int,
    baseAlpha: Float,
    t: Float,
    isDark: Boolean
) {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFCA28).copy(alpha = baseAlpha * (if (isDark) 0.14f else 0.09f)),
                Color.Transparent
            ),
            center = Offset(w * 0.5f, h * 0.85f),
            radius = w * 0.65f
        )
    )
    for (i in 0 until count) {
        val twinkle = 0.60f + 0.40f * sin(t * 2.8f + pool.phase[i])
        val c = DIWALI_COLORS[pool.colorSlot[i] % DIWALI_COLORS.size]
        drawCircle(
            color = c.copy(alpha = baseAlpha * twinkle * 0.78f),
            radius = pool.size[i].dp.toPx() * 0.75f,
            center = Offset(pool.x[i] * w, pool.y[i] * h)
        )
    }
    drawMinimalDiya(Offset(w * 0.14f, h * 0.90f), 9.dp.toPx(), baseAlpha)
    drawMinimalDiya(Offset(w * 0.86f, h * 0.90f), 9.dp.toPx(), baseAlpha)
}

private fun DrawScope.drawFloatingPetals(
    w: Float,
    h: Float,
    pool: FestivalParticlePool,
    count: Int,
    baseAlpha: Float,
    t: Float,
    palette: Array<Color>
) {
    for (i in 0 until count) {
        val px = pool.x[i] * w
        val py = pool.y[i] * h
        val sz = pool.size[i].dp.toPx()
        val color = palette[pool.colorSlot[i] % palette.size]
        val rot = (t * 24f + pool.phase[i] * 57f) % 360f
        rotate(degrees = rot, pivot = Offset(px, py)) {
            drawOval(
                color = color.copy(alpha = baseAlpha * pool.alpha[i]),
                topLeft = Offset(px - sz * 0.65f, py - sz * 0.35f),
                size = Size(sz * 1.3f, sz * 0.7f)
            )
        }
    }
}

private fun DrawScope.drawMinimalDiya(center: Offset, sizePx: Float, alpha: Float) {
    // Soft golden halo
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFD54F).copy(alpha = alpha * 0.42f), Color.Transparent),
            center = Offset(center.x, center.y - sizePx * 0.45f),
            radius = sizePx * 1.85f
        ),
        radius = sizePx * 1.85f,
        center = Offset(center.x, center.y - sizePx * 0.45f)
    )
    // Earthen bowl
    drawArc(
        color = Color(0xFFD84315).copy(alpha = alpha * 0.85f),
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(center.x - sizePx * 0.75f, center.y - sizePx * 0.25f),
        size = Size(sizePx * 1.5f, sizePx * 0.9f)
    )
    // Warm inner flame
    drawOval(
        color = Color(0xFFFFF176).copy(alpha = alpha * 0.95f),
        topLeft = Offset(center.x - sizePx * 0.22f, center.y - sizePx * 0.85f),
        size = Size(sizePx * 0.44f, sizePx * 0.72f)
    )
}
