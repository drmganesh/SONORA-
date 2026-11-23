package ui.components

import android.app.ActivityManager
import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import data.preferences.SnowfallSettings
import ui.theme.EditorialColors
import ui.theme.LocalReduceAnimations
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Reusable zero-allocation particle buffer for the atmospheric snowfall Canvas layer.
 * Stores normalized coordinates (0f..1f) and multi-depth parallax properties.
 */
internal class SnowfallParticlePool(
    val count: Int,
    seed: Int = 20261121
) {
    val x = FloatArray(count)
    val y = FloatArray(count)
    val depth = FloatArray(count)       // 0.25f (far) .. 1.0f (near)
    val baseRadiusDp = FloatArray(count)
    val baseSpeedNorm = FloatArray(count) // screen heights per second
    val driftAmplitudeNorm = FloatArray(count)
    val driftFrequencyRad = FloatArray(count)
    val driftPhaseRad = FloatArray(count)
    val alphaFactor = FloatArray(count)

    private val random = Random(seed)

    init {
        for (i in 0 until count) {
            val d = when {
                i % 5 == 0 -> 0.78f + random.nextFloat() * 0.22f // Near soft flakes
                i % 2 == 0 -> 0.48f + random.nextFloat() * 0.30f // Mid depth flakes
                else -> 0.24f + random.nextFloat() * 0.24f       // Far subtle dust flakes
            }
            depth[i] = d
            x[i] = random.nextFloat()
            y[i] = random.nextFloat()
            baseRadiusDp[i] = 1.15f + d * 2.15f
            baseSpeedNorm[i] = 0.022f + d * 0.056f
            driftAmplitudeNorm[i] = 0.006f + d * 0.015f
            driftFrequencyRad[i] = (0.45f + random.nextFloat() * 0.85f) * (2f * PI.toFloat())
            driftPhaseRad[i] = random.nextFloat() * (2f * PI.toFloat())
            alphaFactor[i] = 0.34f + d * 0.66f
        }
    }

    fun step(deltaSec: Float, elapsedSec: Float, speedMultiplier: Float) {
        if (deltaSec <= 0f) return
        val dt = deltaSec.coerceIn(0f, 0.05f)
        for (i in 0 until count) {
            var nextY = y[i] + baseSpeedNorm[i] * speedMultiplier * dt
            if (nextY > 1.04f) {
                nextY -= 1.08f
                x[i] = (x[i] + 0.137f + random.nextFloat() * 0.29f) % 1.0f
            }
            y[i] = nextY

            // Gentle horizontal breeze drift
            val breeze = sin(elapsedSec * driftFrequencyRad[i] * 0.28f + driftPhaseRad[i]) *
                driftAmplitudeNorm[i] * dt
            var nextX = x[i] + breeze
            if (nextX < -0.04f) nextX += 1.08f
            if (nextX > 1.04f) nextX -= 1.08f
            x[i] = nextX
        }
    }
}

/**
 * Lightweight, lifecycle-aware atmospheric snowfall background Canvas for SONORA Music Home.
 *
 * Performance & design guarantees:
 * - Drawn inside a single Canvas with pre-allocated FloatArrays (no per-frame heap allocations).
 * - Automatically reduces particle density on low-RAM devices and in battery-saving mode.
 * - Completely halts its animation loop when inactive, off-screen, or app is backgrounded.
 * - Smoothly fades in and out when playback state or Home mode changes.
 * - Purely non-interactive background layer; never intercepts touches or covers foreground UI.
 */
@Composable
fun SnowfallOverlay(
    isActive: Boolean,
    settings: SnowfallSettings,
    colors: EditorialColors,
    screenVisible: Boolean = true,
    modifier: Modifier = Modifier,
    testTagName: String = "snowfall_ambient_overlay"
) {
    val context = LocalContext.current
    val globalReduceAnimations = LocalReduceAnimations.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val isLowRamDevice = remember(context) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am?.isLowRamDevice == true
    }

    val visibilityAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(durationMillis = if (isActive) 520 else 440),
        label = "snowfall_visibility_alpha"
    )

    // Skip rendering and animation completely once faded out
    if (!isActive && visibilityAlpha <= 0.005f) return

    val particleCount = remember(settings.intensity, settings.reduceAnimationsForBattery, isLowRamDevice, globalReduceAnimations) {
        settings.effectiveParticleCount(
            isLowRamDevice = isLowRamDevice,
            globalReduceAnimations = globalReduceAnimations
        )
    }
    val pool = remember(particleCount) { SnowfallParticlePool(count = particleCount) }
    val speedMultiplier = settings.effectiveSpeedMultiplier(globalReduceAnimations)
    val minFrameIntervalNs = settings.minFrameIntervalNanos(globalReduceAnimations)
    val shouldAnimate = screenVisible &&
        lifecycleState.isAtLeast(Lifecycle.State.STARTED) &&
        (isActive || visibilityAlpha > 0.01f)

    var frameTick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(shouldAnimate, pool, speedMultiplier, minFrameIntervalNs) {
        if (!shouldAnimate) return@LaunchedEffect
        var lastFrameNs = 0L
        var lastDrawnNs = 0L
        var elapsedSec = 0f
        while (true) {
            withFrameNanos { frameTimeNs ->
                if (lastFrameNs == 0L) {
                    lastFrameNs = frameTimeNs
                    lastDrawnNs = frameTimeNs
                    return@withFrameNanos
                }
                if (minFrameIntervalNs > 0L && (frameTimeNs - lastDrawnNs) < minFrameIntervalNs) {
                    return@withFrameNanos
                }
                val dt = ((frameTimeNs - lastFrameNs) / 1_000_000_000f).coerceIn(0f, 0.05f)
                lastFrameNs = frameTimeNs
                lastDrawnNs = frameTimeNs
                elapsedSec += dt
                pool.step(deltaSec = dt, elapsedSec = elapsedSec, speedMultiplier = speedMultiplier)
                frameTick = frameTimeNs
            }
        }
    }

    // Harmonize flake tint with AMOLED, Dark, Dynamic Album Art, and Light themes
    val baseFlakeColor = remember(colors.isDark, colors.accentStrong, colors.textPrimary) {
        if (colors.isDark) {
            lerp(Color.White, colors.accentStrong, 0.14f)
        } else {
            lerp(colors.accentStrong, Color(0xFF64748B), 0.42f)
        }
    }
    val haloFlakeColor = remember(colors.isDark, colors.accentStrong) {
        if (colors.isDark) {
            lerp(Color.White, colors.accentStrong, 0.28f)
        } else {
            colors.accentStrong
        }
    }

    val masterOpacity = settings.clampedOpacity * visibilityAlpha
    val sizeScale = settings.flakeSize.scaleMultiplier

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                contentDescription = if (isActive) "Ambient snowfall active" else "Ambient snowfall fading"
            }
            .testTag(testTagName)
    ) {
        // Read frameTick inside draw phase only — avoids recomposing parent layouts on every frame
        @Suppress("UNUSED_VARIABLE")
        val tick = frameTick
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f || masterOpacity <= 0.004f) return@Canvas

        val densityPx = density
        val count = pool.count
        for (i in 0 until count) {
            val px = pool.x[i] * w
            val py = pool.y[i] * h
            val radiusPx = pool.baseRadiusDp[i] * sizeScale * densityPx
            val flakeAlpha = (masterOpacity * pool.alphaFactor[i]).coerceIn(0f, 0.92f)

            // Soft atmospheric halo for depth without harsh edges
            if (pool.depth[i] > 0.45f) {
                drawCircle(
                    color = haloFlakeColor.copy(alpha = flakeAlpha * 0.26f),
                    radius = radiusPx * 2.05f,
                    center = Offset(px, py)
                )
            }
            // Crisp soft snowflake core
            drawCircle(
                color = baseFlakeColor.copy(alpha = flakeAlpha),
                radius = radiusPx,
                center = Offset(px, py)
            )
        }
    }
}
