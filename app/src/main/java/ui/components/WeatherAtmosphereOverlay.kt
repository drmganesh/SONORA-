package ui.components

import android.app.ActivityManager
import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.example.model.PlayerStyle
import data.preferences.WeatherAmbientSettings
import domain.weather.DaylightPhase
import domain.weather.PrimaryAtmosphereType
import domain.weather.WeatherAtmospherePlan
import domain.weather.WeatherSurfaceRole
import domain.weather.WmoWeatherCodeInterpreter
import ui.theme.EditorialColors
import ui.theme.LocalReduceAnimations
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Shared zero-allocation simulation pool driving all weather particle layers
 * (rain, snow, wind streaks, fireplace embers, glass window droplets, and subtle stars).
 */
internal class WeatherParticleSimulationPool(
    val capacity: Int,
    seed: Int = 20261123
) {
    val x = FloatArray(capacity)
    val y = FloatArray(capacity)
    val depth = FloatArray(capacity)      // 0.25f (far) .. 1.0f (near)
    val sizeFactor = FloatArray(capacity)
    val speedNorm = FloatArray(capacity)
    val phaseRad = FloatArray(capacity)
    val alphaFactor = FloatArray(capacity)

    // Pre-allocated glass window droplet state (only drawn on Glassmorphism / Liquid Glass in rain)
    val dropletCount = (capacity / 4).coerceIn(6, 22)
    val dropX = FloatArray(dropletCount)
    val dropY = FloatArray(dropletCount)
    val dropRadiusDp = FloatArray(dropletCount)
    val dropSlideSpeed = FloatArray(dropletCount)
    val dropAlpha = FloatArray(dropletCount)

    // Atmospheric phase timers (updated in-place without allocations)
    var elapsedSec: Float = 0f
    var lightningFlashAlpha: Float = 0f
    var lightningOriginX: Float = 0.65f
    private var nextLightningCooldownSec: Float = 6.5f

    private val random = Random(seed)

    init {
        for (i in 0 until capacity) {
            val d = when {
                i % 5 == 0 -> 0.78f + random.nextFloat() * 0.22f
                i % 2 == 0 -> 0.48f + random.nextFloat() * 0.30f
                else -> 0.24f + random.nextFloat() * 0.24f
            }
            depth[i] = d
            x[i] = random.nextFloat()
            y[i] = random.nextFloat()
            sizeFactor[i] = 0.72f + d * 0.85f
            speedNorm[i] = 0.24f + d * 0.68f
            phaseRad[i] = random.nextFloat() * (2f * PI.toFloat())
            alphaFactor[i] = 0.36f + d * 0.64f
        }
        for (i in 0 until dropletCount) {
            dropX[i] = 0.05f + random.nextFloat() * 0.90f
            dropY[i] = random.nextFloat()
            dropRadiusDp[i] = 1.6f + random.nextFloat() * 2.8f
            dropSlideSpeed[i] = 0.014f + random.nextFloat() * 0.036f
            dropAlpha[i] = 0.22f + random.nextFloat() * 0.36f
        }
    }

    fun step(
        deltaSec: Float,
        plan: WeatherAtmospherePlan,
        speedMultiplier: Float
    ) {
        if (deltaSec <= 0f) return
        val dt = deltaSec.coerceIn(0f, 0.05f)
        elapsedSec += dt * speedMultiplier

        // 1. Update primary particle layer
        when {
            plan.rainDensityFactor > 0.02f -> {
                val rainSpeedScale = (1.15f + plan.rainDensityFactor * 0.95f) * speedMultiplier
                val windSlant = plan.windVectorX * 0.32f
                for (i in 0 until capacity) {
                    var ny = y[i] + speedNorm[i] * rainSpeedScale * dt
                    var nx = x[i] + windSlant * speedNorm[i] * dt
                    if (ny > 1.08f) {
                        ny -= 1.16f
                        nx = random.nextFloat()
                    }
                    if (nx < -0.08f) nx += 1.16f
                    if (nx > 1.08f) nx -= 1.16f
                    x[i] = nx
                    y[i] = ny
                }
                if (plan.windowDropletsEnabled) {
                    for (i in 0 until dropletCount) {
                        var dy = dropY[i] + dropSlideSpeed[i] * speedMultiplier * dt
                        if (dy > 1.06f) {
                            dy = -0.04f
                            dropX[i] = 0.05f + random.nextFloat() * 0.90f
                        }
                        dropY[i] = dy
                    }
                }
            }

            plan.snowDensityFactor > 0.02f -> {
                val snowSpeed = 0.14f * speedMultiplier
                val windDrift = plan.windVectorX * 0.07f
                for (i in 0 until capacity) {
                    var ny = y[i] + speedNorm[i] * snowSpeed * dt
                    if (ny > 1.04f) {
                        ny -= 1.08f
                        x[i] = (x[i] + 0.17f + random.nextFloat() * 0.31f) % 1.0f
                    }
                    val sway = sin(elapsedSec * 1.1f + phaseRad[i]) * 0.018f * depth[i]
                    var nx = x[i] + (windDrift + sway) * dt
                    if (nx < -0.04f) nx += 1.08f
                    if (nx > 1.04f) nx -= 1.08f
                    x[i] = nx
                    y[i] = ny
                }
            }

            plan.fireplaceStrength > 0.02f -> {
                // Rising glowing embers from bottom hearth
                val riseSpeed = 0.18f * speedMultiplier
                for (i in 0 until capacity) {
                    var ny = y[i] - speedNorm[i] * riseSpeed * dt
                    if (ny < 0.22f) {
                        ny = 0.98f + random.nextFloat() * 0.08f
                        x[i] = 0.12f + random.nextFloat() * 0.76f
                    }
                    val flickerSway = sin(elapsedSec * 2.2f + phaseRad[i]) * 0.04f
                    var nx = x[i] + flickerSway * dt
                    if (nx < 0.02f) nx = 0.98f
                    if (nx > 0.98f) nx = 0.02f
                    x[i] = nx
                    y[i] = ny
                }
            }

            plan.windParticleFactor > 0.02f -> {
                // Directional flowing wind streaks / drifting leaves
                val dirSign = if (plan.windVectorX >= 0f) 1f else -1f
                val horizontalSpeed = (0.34f + kotlin.math.abs(plan.windVectorX) * 0.42f) * speedMultiplier
                for (i in 0 until capacity) {
                    var nx = x[i] + dirSign * speedNorm[i] * horizontalSpeed * dt
                    val waveY = sin(elapsedSec * 1.4f + phaseRad[i]) * 0.022f
                    var ny = y[i] + waveY * dt
                    if (nx > 1.10f) {
                        nx = -0.10f
                        ny = random.nextFloat()
                    } else if (nx < -0.10f) {
                        nx = 1.10f
                        ny = random.nextFloat()
                    }
                    if (ny < 0.02f) ny = 0.98f
                    if (ny > 0.98f) ny = 0.02f
                    x[i] = nx
                    y[i] = ny
                }
            }
        }

        // 2. Safe, occasional distant lightning pulse for Thunderstorm (7.5s..14s cooldown, soft decay)
        if (plan.lightningStrength > 0.05f) {
            if (lightningFlashAlpha > 0f) {
                lightningFlashAlpha = (lightningFlashAlpha - dt * 3.8f).coerceAtLeast(0f)
            } else {
                nextLightningCooldownSec -= dt
                if (nextLightningCooldownSec <= 0f) {
                    lightningFlashAlpha = 0.34f * plan.lightningStrength
                    lightningOriginX = 0.18f + random.nextFloat() * 0.64f
                    nextLightningCooldownSec = 7.5f + random.nextFloat() * 6.5f
                }
            }
        } else {
            lightningFlashAlpha = 0f
        }
    }
}

/**
 * Reusable, lifecycle-aware, single-Canvas weather ambient effects layer for SONORA.
 *
 * Safe across all surfaces:
 * - Strictly non-interactive background layer (never intercepts gestures or blocks playback controls).
 * - Never alters album artwork sharpness or audio processing.
 * - Automatically pauses frame stepping when invisible, backgrounded, or Off.
 * - Reduces particle budgets & caps frame cadence in Battery Saver / Low-RAM / Reduce Motion modes.
 */
@Composable
fun WeatherAtmosphereOverlay(
    plan: WeatherAtmospherePlan,
    settings: WeatherAmbientSettings,
    colors: EditorialColors,
    surfaceRole: WeatherSurfaceRole,
    isActive: Boolean = plan.isActive,
    screenVisible: Boolean = true,
    modifier: Modifier = Modifier,
    testTagName: String = "weather_atmosphere_overlay"
) {
    val context = LocalContext.current
    val globalReduceAnimations = LocalReduceAnimations.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val isLowRamDevice = remember(context) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am?.isLowRamDevice == true
    }

    val effectiveActive = isActive && plan.isActive
    val visibilityAlpha by animateFloatAsState(
        targetValue = if (effectiveActive) 1f else 0f,
        animationSpec = tween(durationMillis = if (effectiveActive) 560 else 420),
        label = "weather_atmosphere_visibility"
    )

    if (!effectiveActive && visibilityAlpha <= 0.004f) return

    // Surface-appropriate particle base count & opacity balance
    val baseCapacity = when (surfaceRole) {
        is WeatherSurfaceRole.MiniPlayer -> 22
        is WeatherSurfaceRole.SettingsPreview -> 52
        is WeatherSurfaceRole.HomeBackground -> 64
        is WeatherSurfaceRole.FullscreenPlayer -> when (surfaceRole.style) {
            PlayerStyle.AMOLED_MINIMAL, PlayerStyle.NEON -> 48
            else -> 76
        }
    }

    val particleBudget = remember(
        baseCapacity,
        settings.effectIntensity,
        settings.clampedParticleDensity,
        settings.batterySaverMode,
        isLowRamDevice,
        globalReduceAnimations
    ) {
        settings.effectiveParticleBudget(
            baseCount = baseCapacity,
            isLowRamDevice = isLowRamDevice,
            globalReduceAnimations = globalReduceAnimations
        )
    }

    val pool = remember(particleBudget) { WeatherParticleSimulationPool(capacity = particleBudget) }
    val speedMultiplier = settings.effectiveSpeedMultiplier(globalReduceAnimations)
    val minFrameIntervalNs = settings.minFrameIntervalNanos(
        isLowRamDevice = isLowRamDevice,
        globalReduceAnimations = globalReduceAnimations
    )

    val needsContinuousFrames = plan.rainDensityFactor > 0.02f ||
        plan.snowDensityFactor > 0.02f ||
        plan.windParticleFactor > 0.02f ||
        plan.fireplaceStrength > 0.02f ||
        plan.fogDensity > 0.02f ||
        plan.cloudDensity > 0.02f ||
        plan.lightningStrength > 0.02f ||
        plan.clearNightStarStrength > 0.02f ||
        plan.sunlightStrength > 0.02f

    val shouldAnimate = screenVisible &&
        needsContinuousFrames &&
        lifecycleState.isAtLeast(Lifecycle.State.STARTED) &&
        (effectiveActive || visibilityAlpha > 0.01f)

    var frameTick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(shouldAnimate, pool, plan, speedMultiplier, minFrameIntervalNs) {
        if (!shouldAnimate) return@LaunchedEffect
        var lastFrameNs = 0L
        var lastDrawnNs = 0L
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
                pool.step(deltaSec = dt, plan = plan, speedMultiplier = speedMultiplier)
                frameTick = frameTimeNs
            }
        }
    }

    // Surface opacity scaling keeps AMOLED dark, Mini Player subtle, and Foreground sharp
    val surfaceOpacityScale = when (surfaceRole) {
        is WeatherSurfaceRole.MiniPlayer -> 0.56f
        is WeatherSurfaceRole.SettingsPreview -> 1.0f
        is WeatherSurfaceRole.HomeBackground -> 0.86f
        is WeatherSurfaceRole.FullscreenPlayer -> when (surfaceRole.style) {
            PlayerStyle.AMOLED_MINIMAL -> 0.62f
            PlayerStyle.NEON -> 0.72f
            PlayerStyle.GLASS, PlayerStyle.LIQUID_GLASS, PlayerStyle.CINEMATIC -> 0.96f
            else -> 0.84f
        }
    }

    val masterAlpha = (settings.clampedBackgroundOpacity * surfaceOpacityScale * visibilityAlpha)
        .coerceIn(0f, 1f)
    val reusableRayPath = remember { Path() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "Weather atmosphere: ${plan.summaryLabel}"
            }
            .testTag(testTagName)
    ) {
        // Read frameTick strictly inside draw phase to prevent parent recompositions
        @Suppress("UNUSED_VARIABLE")
        val tick = frameTick
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f || masterAlpha <= 0.004f) return@Canvas

        // 1. Subtle atmospheric color wash (harmonized with dark/light/AMOLED themes)
        if (plan.atmosphericTintAlpha > 0.01f) {
            val tintColor = Color(plan.atmosphericTintArgb)
            val effectiveTintAlpha = (plan.atmosphericTintAlpha * masterAlpha * if (colors.isDark) 0.95f else 0.72f)
                .coerceIn(0f, 0.42f)
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        tintColor.copy(alpha = effectiveTintAlpha),
                        tintColor.copy(alpha = effectiveTintAlpha * 0.45f),
                        Color.Transparent
                    )
                )
            )
        }

        // 2. Astronomical Sunlight & Crepuscular Rays (strictly daylight only)
        if (plan.sunlightStrength > 0.02f && plan.solarPosition.isDaylight) {
            drawSunlightLayer(
                plan = plan,
                masterAlpha = masterAlpha,
                elapsedSec = pool.elapsedSec,
                rayPath = reusableRayPath
            )
        }

        // 3. Clear Night subtle twinkling ambient stars
        if (plan.clearNightStarStrength > 0.02f) {
            drawClearNightStars(
                pool = pool,
                strength = plan.clearNightStarStrength,
                masterAlpha = masterAlpha
            )
        }

        // 4. Slow-moving Clouds & Overcast Shadows
        if (plan.cloudDensity > 0.02f) {
            drawCloudLayer(
                cloudDensity = plan.cloudDensity,
                windVectorX = plan.windVectorX,
                elapsedSec = pool.elapsedSec,
                isDarkTheme = colors.isDark,
                masterAlpha = masterAlpha
            )
        }

        // 5. Layered Translucent Fog / Mist
        if (plan.fogDensity > 0.02f) {
            drawFogLayer(
                fogDensity = plan.fogDensity,
                elapsedSec = pool.elapsedSec,
                isDarkTheme = colors.isDark,
                masterAlpha = masterAlpha
            )
        }

        // 6. Cold-Night Fireplace Glow + Rising Embers (strictly behind controls & artwork)
        if (plan.fireplaceStrength > 0.02f) {
            drawFireplaceLayer(
                pool = pool,
                strength = plan.fireplaceStrength,
                masterAlpha = masterAlpha
            )
        }

        // 7. Distant Safe Thunderstorm Flash Glow (brief, subtle, photosensitivity-safe)
        if (pool.lightningFlashAlpha > 0.01f) {
            val flashCenter = Offset(w * pool.lightningOriginX, h * 0.16f)
            val flashAlpha = (pool.lightningFlashAlpha * masterAlpha).coerceIn(0f, 0.34f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFDCEBFF).copy(alpha = flashAlpha),
                        Color(0xFF93C5FD).copy(alpha = flashAlpha * 0.42f),
                        Color.Transparent
                    ),
                    center = flashCenter,
                    radius = maxOf(w, h) * 0.65f
                ),
                radius = maxOf(w, h) * 0.65f,
                center = flashCenter
            )
        }

        // 8. Directional Wind Streaks / Drifting Mist Particles
        if (plan.windParticleFactor > 0.02f && plan.rainDensityFactor <= 0.02f && plan.snowDensityFactor <= 0.02f) {
            drawWindLayer(
                pool = pool,
                plan = plan,
                isDarkTheme = colors.isDark,
                masterAlpha = masterAlpha
            )
        }

        // 9. Realistic Rain Streaks + Wind Slant + Glass Window Droplets
        if (plan.rainDensityFactor > 0.02f) {
            drawRainLayer(
                pool = pool,
                plan = plan,
                isDarkTheme = colors.isDark,
                masterAlpha = masterAlpha
            )
        }

        // 10. Real Snowfall Particles (only when actual snowfall or Manual Snow)
        if (plan.snowDensityFactor > 0.02f) {
            drawSnowLayer(
                pool = pool,
                plan = plan,
                isDarkTheme = colors.isDark,
                accent = colors.accentStrong,
                masterAlpha = masterAlpha
            )
        }
    }
}

private fun DrawScope.drawSunlightLayer(
    plan: WeatherAtmospherePlan,
    masterAlpha: Float,
    elapsedSec: Float,
    rayPath: Path
) {
    val w = size.width
    val h = size.height
    val sunX = w * plan.solarPosition.normalizedSunX
    val sunY = h * plan.solarPosition.normalizedSunY
    val sunCenter = Offset(sunX, sunY)

    val (coreColor, haloColor) = when (plan.solarPosition.phase) {
        DaylightPhase.SUNRISE -> Color(0xFFFFB74D) to Color(0xFFFF7043)
        DaylightPhase.SUNSET -> Color(0xFFFF8A65) to Color(0xFFE64A19)
        DaylightPhase.MIDDAY -> Color(0xFFFFF59D) to Color(0xFFFFD54F)
        else -> Color(0xFFFFE082) to Color(0xFFFFB74D)
    }

    val breathe = 0.94f + 0.06f * sin(elapsedSec * 0.45f)
    val glowRadius = maxOf(w, h) * (0.58f + plan.sunlightStrength * 0.25f)
    val glowAlpha = (0.34f * plan.sunlightStrength * masterAlpha * breathe).coerceIn(0f, 0.42f)

    // Warm atmospheric solar glow radiating from actual astronomical sun position
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                coreColor.copy(alpha = glowAlpha),
                haloColor.copy(alpha = glowAlpha * 0.46f),
                Color.Transparent
            ),
            center = sunCenter,
            radius = glowRadius
        ),
        radius = glowRadius,
        center = sunCenter
    )

    // Subtle directional golden light rays rotated along actual solar ray vector
    val baseAngle = plan.solarPosition.rayDirectionRadians
    val rayLength = maxOf(w, h) * 0.85f
    val rayAlpha = (0.13f * plan.sunlightStrength * masterAlpha * breathe).coerceIn(0f, 0.18f)
    for (r in -2..2) {
        val rayAngle = baseAngle + r * 0.16f + sin(elapsedSec * 0.22f + r) * 0.02f
        val halfSpread = 0.065f
        val x1 = sunX + cos(rayAngle - halfSpread) * rayLength
        val y1 = sunY + sin(rayAngle - halfSpread) * rayLength
        val x2 = sunX + cos(rayAngle + halfSpread) * rayLength
        val y2 = sunY + sin(rayAngle + halfSpread) * rayLength

        rayPath.reset()
        rayPath.moveTo(sunX, sunY)
        rayPath.lineTo(x1, y1)
        rayPath.lineTo(x2, y2)
        rayPath.close()

        drawPath(
            path = rayPath,
            brush = Brush.radialGradient(
                colors = listOf(
                    coreColor.copy(alpha = rayAlpha),
                    haloColor.copy(alpha = rayAlpha * 0.28f),
                    Color.Transparent
                ),
                center = sunCenter,
                radius = rayLength
            )
        )
    }
}

private fun DrawScope.drawCloudLayer(
    cloudDensity: Float,
    windVectorX: Float,
    elapsedSec: Float,
    isDarkTheme: Boolean,
    masterAlpha: Float
) {
    val w = size.width
    val h = size.height
    val cloudBaseColor = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
    val driftSpeed = 0.012f + kotlin.math.abs(windVectorX) * 0.014f
    val driftSign = if (windVectorX >= 0f) 1f else -1f

    val bandCount = 4
    for (i in 0 until bandCount) {
        val rawX = (0.15f + i * 0.28f + elapsedSec * driftSpeed * (0.7f + i * 0.2f) * driftSign)
        val normX = ((rawX % 1.4f) + 1.4f) % 1.4f - 0.2f
        val normY = 0.10f + i * 0.11f
        val rx = w * (0.36f + (i % 2) * 0.14f)
        val ry = h * (0.10f + (i % 3) * 0.035f)
        val alpha = (0.14f * cloudDensity * masterAlpha * (0.85f - i * 0.12f)).coerceIn(0f, 0.22f)

        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    cloudBaseColor.copy(alpha = alpha),
                    cloudBaseColor.copy(alpha = alpha * 0.35f),
                    Color.Transparent
                ),
                center = Offset(w * normX, h * normY),
                radius = maxOf(rx, ry)
            ),
            topLeft = Offset(w * normX - rx, h * normY - ry),
            size = Size(rx * 2f, ry * 2f)
        )
    }
}

private fun DrawScope.drawFogLayer(
    fogDensity: Float,
    elapsedSec: Float,
    isDarkTheme: Boolean,
    masterAlpha: Float
) {
    val w = size.width
    val h = size.height
    val fogColor = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF94A3B8)
    for (band in 0 until 3) {
        val shift = sin(elapsedSec * (0.18f + band * 0.07f) + band * 1.9f) * w * 0.14f
        val yCenter = h * (0.28f + band * 0.26f)
        val bandHeight = h * 0.34f
        val bandAlpha = (0.17f * fogDensity * masterAlpha).coerceIn(0f, 0.26f)
        drawOval(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    fogColor.copy(alpha = bandAlpha),
                    fogColor.copy(alpha = bandAlpha * 0.65f),
                    Color.Transparent
                ),
                startY = yCenter - bandHeight / 2f,
                endY = yCenter + bandHeight / 2f
            ),
            topLeft = Offset(-w * 0.25f + shift, yCenter - bandHeight / 2f),
            size = Size(w * 1.5f, bandHeight)
        )
    }
}

private fun DrawScope.drawFireplaceLayer(
    pool: WeatherParticleSimulationPool,
    strength: Float,
    masterAlpha: Float
) {
    val w = size.width
    val h = size.height
    val flicker = 0.90f + 0.06f * sin(pool.elapsedSec * 5.2f) + 0.04f * sin(pool.elapsedSec * 8.7f)
    val glowCenter = Offset(w * 0.50f, h * 0.98f)
    val glowRadius = maxOf(w, h) * 0.72f
    val glowAlpha = (0.34f * strength * masterAlpha * flicker).coerceIn(0f, 0.44f)

    // Warm bottom hearth illumination (strictly behind artwork & controls)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFA726).copy(alpha = glowAlpha),
                Color(0xFFFF6D00).copy(alpha = glowAlpha * 0.55f),
                Color(0xFFBF360C).copy(alpha = glowAlpha * 0.22f),
                Color.Transparent
            ),
            center = glowCenter,
            radius = glowRadius
        ),
        radius = glowRadius,
        center = glowCenter
    )

    // Subtle rising glowing ember particles
    if (pool.capacity <= 0) return
    val activeEmbers = (pool.capacity * 0.55f * strength).toInt()
        .coerceIn(minOf(8, pool.capacity), pool.capacity)
    for (i in 0 until activeEmbers) {
        val px = pool.x[i] * w
        val py = pool.y[i] * h
        // Fade embers smoothly as they rise higher
        val heightFade = ((py - h * 0.22f) / (h * 0.76f)).coerceIn(0f, 1f)
        val alpha = (0.72f * pool.alphaFactor[i] * heightFade * masterAlpha * flicker).coerceIn(0f, 0.85f)
        val radiusPx = (1.1f + pool.depth[i] * 1.6f) * density
        val emberColor = if (i % 3 == 0) Color(0xFFFFD54F) else Color(0xFFFF8A3D)
        drawCircle(
            color = emberColor.copy(alpha = alpha * 0.32f),
            radius = radiusPx * 2.2f,
            center = Offset(px, py)
        )
        drawCircle(
            color = emberColor.copy(alpha = alpha),
            radius = radiusPx,
            center = Offset(px, py)
        )
    }
}

private fun DrawScope.drawWindLayer(
    pool: WeatherParticleSimulationPool,
    plan: WeatherAtmospherePlan,
    isDarkTheme: Boolean,
    masterAlpha: Float
) {
    val w = size.width
    val h = size.height
    if (pool.capacity <= 0) return
    val activeCount = (pool.capacity * (0.45f + plan.windParticleFactor * 0.55f))
        .toInt()
        .coerceIn(minOf(8, pool.capacity), pool.capacity)
    val dirSign = if (plan.windVectorX >= 0f) 1f else -1f
    val streakColor = if (isDarkTheme) Color(0xFFD8E6F2) else Color(0xFF64748B)
    val leafColor = if (isDarkTheme) Color(0xFFA5D6A7) else Color(0xFF4CAF50)

    for (i in 0 until activeCount) {
        val px = pool.x[i] * w
        val py = pool.y[i] * h
        val alpha = (0.34f * pool.alphaFactor[i] * plan.windParticleFactor * masterAlpha).coerceIn(0f, 0.52f)
        if (i % 7 == 0) {
            // Occasional subtle drifting leaf/dust speck
            val r = (1.6f + pool.depth[i] * 1.4f) * density
            drawOval(
                color = leafColor.copy(alpha = alpha * 0.85f),
                topLeft = Offset(px - r, py - r * 0.55f),
                size = Size(r * 2.4f, r * 1.15f)
            )
        } else {
            val lenPx = (18f + pool.depth[i] * 34f * (0.7f + plan.windParticleFactor * 0.5f)) * density
            val slopeY = sin(pool.elapsedSec * 1.3f + pool.phaseRad[i]) * 4f * density
            drawLine(
                color = streakColor.copy(alpha = alpha),
                start = Offset(px, py),
                end = Offset(px - dirSign * lenPx, py - slopeY),
                strokeWidth = (0.9f + pool.depth[i] * 0.75f) * density,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun DrawScope.drawRainLayer(
    pool: WeatherParticleSimulationPool,
    plan: WeatherAtmospherePlan,
    isDarkTheme: Boolean,
    masterAlpha: Float
) {
    val w = size.width
    val h = size.height
    if (pool.capacity <= 0) return
    val activeDrops = (pool.capacity * (0.28f + plan.rainDensityFactor * 0.72f))
        .toInt()
        .coerceIn(minOf(10, pool.capacity), pool.capacity)
    val rainColor = if (isDarkTheme) Color(0xFFC8E1FF) else Color(0xFF47698A)
    val windSlantPx = plan.windVectorX * 9.5f * density

    for (i in 0 until activeDrops) {
        val px = pool.x[i] * w
        val py = pool.y[i] * h
        val d = pool.depth[i]
        val lengthPx = (10f + d * 18f * (0.65f + plan.rainDensityFactor * 0.55f)) * density
        val dropAlpha = ((0.22f + d * 0.42f) * (0.65f + plan.rainDensityFactor * 0.35f) * masterAlpha)
            .coerceIn(0f, 0.72f)
        val strokePx = (0.75f + d * 0.85f) * density

        drawLine(
            color = rainColor.copy(alpha = dropAlpha),
            start = Offset(px, py),
            end = Offset(px - windSlantPx * d, py - lengthPx),
            strokeWidth = strokePx,
            cap = StrokeCap.Round
        )
    }

    // Subtle glass-window water droplets on Glassmorphism / Liquid Glass player styles
    if (plan.windowDropletsEnabled) {
        for (i in 0 until pool.dropletCount) {
            val dx = pool.dropX[i] * w
            val dy = pool.dropY[i] * h
            val r = pool.dropRadiusDp[i] * density
            val alpha = (pool.dropAlpha[i] * masterAlpha).coerceIn(0f, 0.45f)
            // Soft outer rim + crisp specular dot
            drawCircle(
                color = Color.White.copy(alpha = alpha * 0.35f),
                radius = r,
                center = Offset(dx, dy)
            )
            drawCircle(
                color = Color.White.copy(alpha = alpha * 0.75f),
                radius = r * 0.34f,
                center = Offset(dx - r * 0.28f, dy - r * 0.28f)
            )
        }
    }
}

private fun DrawScope.drawSnowLayer(
    pool: WeatherParticleSimulationPool,
    plan: WeatherAtmospherePlan,
    isDarkTheme: Boolean,
    accent: Color,
    masterAlpha: Float
) {
    val w = size.width
    val h = size.height
    if (pool.capacity <= 0) return
    val activeFlakes = (pool.capacity * (0.35f + plan.snowDensityFactor * 0.65f))
        .toInt()
        .coerceIn(minOf(10, pool.capacity), pool.capacity)
    val flakeCore = if (isDarkTheme) Color.White else accent.copy(alpha = 0.85f)

    for (i in 0 until activeFlakes) {
        val px = pool.x[i] * w
        val py = pool.y[i] * h
        val d = pool.depth[i]
        val radiusPx = (1.15f + d * 2.0f) * density
        val alpha = (pool.alphaFactor[i] * masterAlpha * 0.82f).coerceIn(0f, 0.88f)
        if (d > 0.48f) {
            drawCircle(
                color = flakeCore.copy(alpha = alpha * 0.26f),
                radius = radiusPx * 2.0f,
                center = Offset(px, py)
            )
        }
        drawCircle(
            color = flakeCore.copy(alpha = alpha),
            radius = radiusPx,
            center = Offset(px, py)
        )
    }
}

private fun DrawScope.drawClearNightStars(
    pool: WeatherParticleSimulationPool,
    strength: Float,
    masterAlpha: Float
) {
    val w = size.width
    val h = size.height
    if (pool.capacity <= 0) return
    val maxStars = minOf(30, pool.capacity)
    val minStars = minOf(10, maxStars)
    val starCount = (pool.capacity / 2).coerceIn(minStars, maxStars)
    for (i in 0 until starCount) {
        // Keep stars gracefully in upper 58% sky zone
        val sx = pool.x[i] * w
        val sy = (pool.y[i] * 0.56f) * h
        val twinkle = 0.55f + 0.45f * sin(pool.elapsedSec * (0.9f + pool.depth[i]) + pool.phaseRad[i])
        val alpha = (0.48f * strength * twinkle * masterAlpha).coerceIn(0f, 0.62f)
        val r = (0.75f + pool.depth[i] * 0.95f) * density
        drawCircle(
            color = Color(0xFFE2E8F0).copy(alpha = alpha),
            radius = r,
            center = Offset(sx, sy)
        )
    }
}

/**
 * Maps a WMO weather code & daylight flag to a crisp Material Rounded icon for location cards and status badges.
 */
fun weatherIconForCode(weatherCode: Int, isDay: Boolean): ImageVector = when {
    WmoWeatherCodeInterpreter.isThunderstorm(weatherCode) -> Icons.Rounded.Thunderstorm
    WmoWeatherCodeInterpreter.isHeavyRain(weatherCode) ||
        WmoWeatherCodeInterpreter.isModerateOrLightRain(weatherCode) -> Icons.Rounded.WaterDrop
    WmoWeatherCodeInterpreter.isSnowCode(weatherCode) -> Icons.Rounded.AcUnit
    WmoWeatherCodeInterpreter.isFogCode(weatherCode) -> Icons.Rounded.CloudQueue
    WmoWeatherCodeInterpreter.isCloudyOrOvercast(weatherCode) -> Icons.Rounded.Cloud
    isDay -> Icons.Rounded.WbSunny
    else -> Icons.Rounded.NightsStay
}

/**
 * Maps a [PrimaryAtmosphereType] to a crisp Material Rounded icon for preview pills and settings rows.
 */
fun atmosphereIconForPrimary(primary: PrimaryAtmosphereType): ImageVector = when (primary) {
    PrimaryAtmosphereType.THUNDERSTORM -> Icons.Rounded.Thunderstorm
    PrimaryAtmosphereType.HEAVY_RAIN, PrimaryAtmosphereType.MODERATE_OR_LIGHT_RAIN -> Icons.Rounded.WaterDrop
    PrimaryAtmosphereType.SNOWFALL -> Icons.Rounded.AcUnit
    PrimaryAtmosphereType.FOG_OR_MIST -> Icons.Rounded.CloudQueue
    PrimaryAtmosphereType.STRONG_WIND -> Icons.Rounded.Air
    PrimaryAtmosphereType.SUNNY_DAY -> Icons.Rounded.WbSunny
    PrimaryAtmosphereType.CLOUDY_OVERCAST -> Icons.Rounded.Cloud
    PrimaryAtmosphereType.CLEAR_NIGHT -> Icons.Rounded.NightsStay
    PrimaryAtmosphereType.COLD_NIGHT_FIREPLACE -> Icons.Rounded.LocalFireDepartment
    PrimaryAtmosphereType.NONE -> Icons.Rounded.CloudQueue
}
