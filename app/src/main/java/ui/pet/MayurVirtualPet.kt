package ui.pet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.Song
import com.example.ui.theme.PlusJakartaFontFamily
import domain.festival.ActiveFestivalResolution
import domain.pet.HitZoneProjection
import domain.pet.Mayur3DMath
import domain.pet.Mayur3DRigController
import domain.pet.MayurAudioTelemetry
import domain.pet.MayurPetBehaviorEngine
import domain.pet.MayurPetSettings
import domain.pet.MayurReactionEvent
import domain.weather.WeatherAtmospherePlan
import domain.weather.WeatherSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Interactive, full-360° 3D procedural virtual peacock companion **MAYUR**.
 *
 * - Real 3D skeletal hierarchy & depth-sorted rendering (no flat PNG stickers).
 * - Full 360° horizontal spin + vertical viewing tilt with smooth inertia & stopping.
 * - 3D depth-aware touch hit-testing for Head, Headphones, Tail, and Body across every angle,
 *   plus double-tap happy burst and long-press 360° playful flourish.
 * - Music-reactive dancing across 7 styles synchronized to real playback & visualizer beats.
 * - Zero continuous Compose recompositions: physics tick updates only the Canvas draw phase
 *   and pauses automatically when backgrounded or hidden.
 */
@Composable
fun MayurVirtualPet(
    settings: MayurPetSettings,
    currentSong: Song?,
    isPlaying: Boolean,
    visualizerFrequenciesFlow: StateFlow<FloatArray>?,
    waveformSamplesFlow: StateFlow<FloatArray>?,
    weatherPlan: WeatherAtmospherePlan,
    weatherSnapshot: WeatherSnapshot?,
    activeFestival: ActiveFestivalResolution,
    reduceAnimations: Boolean,
    screenVisible: Boolean,
    size: Dp = 128.dp,
    showStatusPill: Boolean = false,
    overrideLocalMinutesOfDay: Int? = null,
    initialOrbitYawDeg: Float = 16f,
    initialOrbitPitchDeg: Float = 8f,
    onReactionTriggered: ((MayurReactionEvent) -> Unit)? = null,
    onOrbitChanged: ((yawDeg: Float, pitchDeg: Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (!settings.enabled) return

    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val touchSlopPx = with(density) { 6.dp.toPx() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Pause animation completely when app goes to background
    var isLifecycleResumed by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isLifecycleResumed = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> isLifecycleResumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isActivelyVisible = screenVisible && isLifecycleResumed
    val canAnimate = isActivelyVisible && !reduceAnimations

    // Core 3D skeletal rig, renderer, and reusable 3D hit-zone list
    val rig = remember { Mayur3DRigController(initialOrbitYawDeg, initialOrbitPitchDeg) }
    val renderer = remember { Mayur3DRenderer() }
    val projectedHitZones = remember { ArrayList<HitZoneProjection>(8) }
    var frameTick by remember { mutableLongStateOf(0L) }

    // Local time minute ticker (checked once every 60s when visible)
    var liveMinutesOfDay by remember {
        val now = LocalTime.now()
        mutableIntStateOf(now.hour * 60 + now.minute)
    }
    LaunchedEffect(isActivelyVisible, settings.nighttimeSleepEnabled, overrideLocalMinutesOfDay) {
        if (overrideLocalMinutesOfDay != null || !isActivelyVisible) return@LaunchedEffect
        while (isActive) {
            val now = LocalTime.now()
            liveMinutesOfDay = now.hour * 60 + now.minute
            val secondsRemaining = (60 - now.second).coerceIn(5, 60)
            delay(secondsRemaining * 1000L)
        }
    }
    val effectiveMinutesOfDay = overrideLocalMinutesOfDay ?: liveMinutesOfDay

    // Sleep wake disturbance state
    var disturbedUntilMs by remember { mutableLongStateOf(0L) }
    var isRecentlyDisturbed by remember { mutableStateOf(false) }

    LaunchedEffect(disturbedUntilMs) {
        if (disturbedUntilMs <= 0L) return@LaunchedEffect
        val remaining = disturbedUntilMs - System.currentTimeMillis()
        if (remaining > 0L) delay(remaining)
        isRecentlyDisturbed = false
    }

    val sleepResolution = remember(settings, effectiveMinutesOfDay, isRecentlyDisturbed) {
        MayurPetBehaviorEngine.resolveSleepState(
            settings = settings,
            localMinutesOfDay = effectiveMinutesOfDay,
            recentlyDisturbedWhileSleeping = isRecentlyDisturbed
        )
    }

    // Dynamic environment costume from existing app Festival Calendar & Weather systems
    val costume = remember(settings, sleepResolution.isSleepWindow, weatherPlan, weatherSnapshot, activeFestival) {
        MayurPetBehaviorEngine.resolveCostume(
            settings = settings,
            isSleepWindow = sleepResolution.isSleepWindow,
            weatherPlan = weatherPlan,
            weatherSnapshot = weatherSnapshot,
            activeFestival = activeFestival
        )
    }

    // Real audio energy & bass beat-onset sampler (low rate, zero ExoPlayer interference)
    var telemetryHolder by remember { mutableStateOf(MayurAudioTelemetry.SILENT) }
    var dancePhaseBucket by remember { mutableIntStateOf(0) }

    val shouldDance = canAnimate &&
        settings.musicDancingEnabled &&
        isPlaying &&
        !sleepResolution.isSleeping

    LaunchedEffect(shouldDance, currentSong?.id, settings.animationQuality, settings.danceIntensity) {
        if (!shouldDance) {
            telemetryHolder = MayurAudioTelemetry.SILENT
            return@LaunchedEffect
        }
        val intervalMs = settings.animationQuality.audioSampleIntervalMs.coerceIn(45L, 160L)
        var bassEma = 0.16f
        var ticks = 0
        var fallbackPhase = 0f

        while (isActive) {
            val freqs = visualizerFrequenciesFlow?.value ?: FloatArray(0)
            val wave = waveformSamplesFlow?.value ?: FloatArray(0)
            val raw = MayurPetBehaviorEngine.computeAudioEnergy(freqs, wave, previousBassEma = bassEma)

            val effective = if (raw.overallAmplitude < 0.025f) {
                // Smooth musical rhythm fallback only when device AudioSession Visualizer returns silence
                fallbackPhase = (fallbackPhase + 0.16f * settings.danceIntensity.speedMultiplier) % 1f
                val pulseWave = sin(fallbackPhase * 2f * PI.toFloat()).coerceAtLeast(0f)
                MayurAudioTelemetry(
                    overallAmplitude = 0.28f + pulseWave * 0.22f,
                    bassEnergy = 0.24f + pulseWave * 0.26f,
                    midEnergy = 0.22f + pulseWave * 0.14f,
                    trebleEnergy = 0.18f + pulseWave * 0.12f,
                    beatPulse = if (pulseWave > 0.86f) 0.72f else pulseWave * 0.45f,
                    isBeatHit = pulseWave > 0.90f
                )
            } else {
                raw
            }

            bassEma = bassEma * 0.80f + effective.bassEnergy * 0.20f
            telemetryHolder = MayurAudioTelemetry(
                overallAmplitude = telemetryHolder.overallAmplitude * 0.45f + effective.overallAmplitude * 0.55f,
                bassEnergy = telemetryHolder.bassEnergy * 0.45f + effective.bassEnergy * 0.55f,
                midEnergy = telemetryHolder.midEnergy * 0.50f + effective.midEnergy * 0.50f,
                trebleEnergy = telemetryHolder.trebleEnergy * 0.55f + effective.trebleEnergy * 0.45f,
                beatPulse = maxOf(effective.beatPulse, telemetryHolder.beatPulse * 0.65f),
                isBeatHit = effective.isBeatHit
            )

            ticks++
            if (ticks % 64 == 0) dancePhaseBucket++
            delay(intervalMs)
        }
    }

    val activeDanceStyle = remember(
        currentSong?.id,
        telemetryHolder.bassEnergy >= 0.44f,
        telemetryHolder.overallAmplitude >= 0.58f,
        settings.danceIntensity,
        dancePhaseBucket
    ) {
        MayurPetBehaviorEngine.selectDanceStyle(
            song = currentSong,
            telemetry = telemetryHolder,
            intensity = settings.danceIntensity,
            phaseBucket = dancePhaseBucket
        )
    }

    // Single-pass 3D animation frame clock — invalidates ONLY Canvas draw phase, never recomposes!
    LaunchedEffect(
        canAnimate,
        shouldDance,
        activeDanceStyle,
        settings.danceIntensity,
        settings.animationQuality,
        sleepResolution.isSleeping
    ) {
        if (!canAnimate) {
            rig.step(
                dtSeconds = 0.016f,
                isDancing = false,
                danceStyle = activeDanceStyle,
                danceIntensity = settings.danceIntensity,
                telemetry = MayurAudioTelemetry.SILENT,
                isSleeping = sleepResolution.isSleeping,
                reduceAnimations = true
            )
            frameTick++
            return@LaunchedEffect
        }

        val minFrameNanos = settings.animationQuality.targetFrameIntervalMs * 1_000_000L
        var lastFrameNanos = 0L
        while (isActive) {
            withFrameNanos { nowNanos ->
                if (lastFrameNanos == 0L) {
                    lastFrameNanos = nowNanos
                }
                val elapsedNanos = nowNanos - lastFrameNanos
                if (elapsedNanos >= minFrameNanos) {
                    val dt = (elapsedNanos / 1_000_000_000f).coerceIn(0.008f, 0.055f)
                    lastFrameNanos = nowNanos
                    rig.step(
                        dtSeconds = dt,
                        isDancing = shouldDance,
                        danceStyle = activeDanceStyle,
                        danceIntensity = settings.danceIntensity,
                        telemetry = telemetryHolder,
                        isSleeping = sleepResolution.isSleeping,
                        reduceAnimations = false
                    )
                    frameTick++
                }
            }
        }
    }

    var interactionCounter by remember { mutableIntStateOf(0) }

    fun dispatchTap(
        tapX: Float,
        tapY: Float,
        widthPx: Float,
        heightPx: Float,
        isDoubleTap: Boolean,
        isLongPress: Boolean
    ) {
        val now = System.currentTimeMillis()
        interactionCounter++
        val zone = Mayur3DMath.hitTestProjectedZones(tapX, tapY, projectedHitZones)
        val normX = if (widthPx > 0f) ((tapX / widthPx) * 2f - 1f).coerceIn(-1f, 1f) else 0f
        val normY = if (heightPx > 0f) ((tapY / heightPx) * 2f - 1f).coerceIn(-1f, 1f) else 0f

        val wasSleeping = sleepResolution.isSleeping
        if (wasSleeping) {
            isRecentlyDisturbed = true
            disturbedUntilMs = now + 2600L
        }

        val event = MayurPetBehaviorEngine.resolveTouchReaction(
            zone = zone,
            isDoubleTap = isDoubleTap && !wasSleeping,
            isLongPress = isLongPress && !wasSleeping,
            isSleeping = wasSleeping,
            variantSeed = interactionCounter + zone.ordinal * 7,
            eventId = now,
            touchNormalizedX = normX,
            touchNormalizedY = normY
        )
        haptic.performHapticFeedback(
            if (isLongPress) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
        )
        rig.triggerReaction(event)
        if (!canAnimate) {
            rig.step(
                dtSeconds = 0.016f,
                isDancing = false,
                danceStyle = activeDanceStyle,
                danceIntensity = settings.danceIntensity,
                telemetry = MayurAudioTelemetry.SILENT,
                isSleeping = sleepResolution.isSleeping,
                reduceAnimations = true
            )
            frameTick++
        }
        onReactionTriggered?.invoke(event)
    }

    val semanticState = buildString {
        append(rig.currentPose.expression.label)
        append(", 3D 360 degree view, ")
        append(costume.displayLabel)
        if (sleepResolution.isSleeping) append(", sleeping")
        else if (shouldDance) append(", dancing ${activeDanceStyle.label}")
    }

    Box(
        modifier = modifier
            .size(size)
            .semantics {
                contentDescription = "Mayur 3D interactive virtual peacock pet"
                stateDescription = semanticState
                role = Role.Button
            }
            // Tap (Head/Body/Tail/Headphones), Double Tap (Special Happy), Long Press (360° Playful Flourish)
            .pointerInput(settings.touchReactionsEnabled, sleepResolution.isSleeping) {
                if (!settings.touchReactionsEnabled) return@pointerInput
                detectTapGestures(
                    onTap = { offset ->
                        dispatchTap(
                            tapX = offset.x,
                            tapY = offset.y,
                            widthPx = this.size.width.toFloat(),
                            heightPx = this.size.height.toFloat(),
                            isDoubleTap = false,
                            isLongPress = false
                        )
                    },
                    onDoubleTap = { offset ->
                        dispatchTap(
                            tapX = offset.x,
                            tapY = offset.y,
                            widthPx = this.size.width.toFloat(),
                            heightPx = this.size.height.toFloat(),
                            isDoubleTap = true,
                            isLongPress = false
                        )
                    },
                    onLongPress = { offset ->
                        dispatchTap(
                            tapX = offset.x,
                            tapY = offset.y,
                            widthPx = this.size.width.toFloat(),
                            heightPx = this.size.height.toFloat(),
                            isDoubleTap = false,
                            isLongPress = true
                        )
                    }
                )
            }
            // Full 360° horizontal rotation & vertical viewing-angle tilt with smooth inertia
            .pointerInput(settings.touchReactionsEnabled) {
                if (!settings.touchReactionsEnabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var totalDx = 0f
                    var totalDy = 0f
                    var dragActive = false
                    var lastEventTimeMs = System.currentTimeMillis()
                    val widthPx = this.size.width.toFloat().coerceAtLeast(100f)
                    val heightPx = this.size.height.toFloat().coerceAtLeast(100f)

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val delta = change.positionChange()
                        totalDx += delta.x
                        totalDy += delta.y

                        if (!dragActive && (abs(totalDx) > touchSlopPx || abs(totalDy) > touchSlopPx)) {
                            dragActive = true
                            rig.beginDrag()
                        }

                        if (dragActive) {
                            change.consume()
                            val nowMs = System.currentTimeMillis()
                            val dtSec = ((nowMs - lastEventTimeMs) / 1000f).coerceIn(0.008f, 0.05f)
                            lastEventTimeMs = nowMs

                            val deltaYaw = (delta.x / widthPx) * 240f
                            val deltaPitch = (delta.y / heightPx) * 110f
                            rig.applyDragDelta(deltaYaw, deltaPitch, dtSec)
                            if (!canAnimate) {
                                rig.step(
                                    dtSeconds = 0.016f,
                                    isDancing = false,
                                    danceStyle = activeDanceStyle,
                                    danceIntensity = settings.danceIntensity,
                                    telemetry = MayurAudioTelemetry.SILENT,
                                    isSleeping = sleepResolution.isSleeping,
                                    reduceAnimations = true
                                )
                                frameTick++
                            }
                            onOrbitChanged?.invoke(rig.orbitYawDeg, rig.orbitPitchDeg)
                        }
                    }

                    if (dragActive) {
                        rig.endDrag()
                        onOrbitChanged?.invoke(rig.orbitYawDeg, rig.orbitPitchDeg)
                    }
                }
            }
            .testTag("mayur_virtual_pet"),
        contentAlignment = Alignment.Center
    ) {
        // Single hardware-accelerated Canvas reading frameTick inside draw phase only!
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("mayur_3d_canvas")
        ) {
            @Suppress("UNUSED_EXPRESSION")
            frameTick
            renderer.render(
                scope = this,
                pose = rig.currentPose,
                costume = costume,
                quality = settings.animationQuality,
                outHitZones = projectedHitZones
            )
        }

        if (sleepResolution.isSleeping) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(2.dp)
                    .testTag("mayur_pet_sleep_indicator")
            )
        }

        AnimatedVisibility(
            visible = showStatusPill,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 2.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xCC121A2B),
                modifier = Modifier.testTag("mayur_pet_costume_badge")
            ) {
                val angleLabel = "${Mayur3DMath.wrapAngle360(rig.orbitYawDeg).toInt()}°"
                Text(
                    text = if (sleepResolution.isSleeping) {
                        "Sleeping • ${costume.displayLabel}"
                    } else if (shouldDance) {
                        "${activeDanceStyle.label} • $angleLabel"
                    } else {
                        "${rig.currentPose.expression.label} • ${costume.displayLabel} • $angleLabel"
                    },
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    color = Color(0xFFFFE082),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}
