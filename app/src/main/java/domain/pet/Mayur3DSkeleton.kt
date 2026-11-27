package domain.pet

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Snapshot of all hierarchical 3D skeletal joint angles, offsets, facial expression channels,
 * and secondary follow-through values for a single render frame.
 */
data class MayurBonePose(
    // Camera orbit angles (full 360° yaw + vertical viewing tilt)
    val orbitYawDeg: Float = 16f,
    val orbitPitchDeg: Float = 8f,

    // Root / Pelvis / Torso transforms
    val bodyYawDeg: Float = 0f,
    val bodyPitchDeg: Float = 0f,
    val bodyRollDeg: Float = 0f,
    val bodyHopY: Float = 0f,
    val bodyShiftX: Float = 0f,
    val breathScale: Float = 1f,

    // Independent Neck / Head transforms (relative to Torso)
    val headYawDeg: Float = 0f,
    val headPitchDeg: Float = 0f,
    val headRollDeg: Float = 0f,

    // Expressive facial animation channels
    val eyeLookX: Float = 0f,       // -1 left .. +1 right
    val eyeLookY: Float = 0f,       // -1 down .. +1 up
    val blinkAmount: Float = 0f,    // 0 open .. 1 closed
    val smileAmount: Float = 0.25f, // 0 neutral .. 1 joyful crescent smile
    val beakOpenAmount: Float = 0f, // 0 closed .. 1 open chirp
    val blushAlpha: Float = 0.28f,

    // Articulated wings (left = -X, right = +X)
    val leftWingFlapDeg: Float = 0f,
    val leftWingPitchDeg: Float = 0f,
    val rightWingFlapDeg: Float = 0f,
    val rightWingPitchDeg: Float = 0f,

    // Articulated feet for steps, hops & dance footwork
    val leftFootLift: Float = 0f,
    val rightFootLift: Float = 0f,
    val leftFootForward: Float = 0f,
    val rightFootForward: Float = 0f,

    // Peacock tail with spring-damper follow-through & spread
    val tailSpread: Float = 0.62f,  // 0.35 folded .. 1.15 full majestic fan
    val tailLiftDeg: Float = 18f,
    val tailLagYawDeg: Float = 0f,
    val tailShakePhase: Float = 0f,
    val tailShakeAmp: Float = 0f,

    // Crown feather bob follow-through
    val crownLagPitchDeg: Float = 0f,
    val crownLagRollDeg: Float = 0f,

    // Jacket glowing audio waveform emblem intensity & beat pulse
    val waveformGlow: Float = 0.35f,
    val beatPulse: Float = 0f,
    val audioAmplitude: Float = 0f,
    val elapsedTimeSec: Float = 0f,

    // Active high-level flags
    val isDancing: Boolean = false,
    val isSleeping: Boolean = false,
    val expression: MayurExpression = MayurExpression.RELAXED,
    val activeReactionType: MayurReactionType? = null,
    val reactionProgress: Float = 0f
)

/**
 * Real-time procedural skeletal rig & physics solver for the 3D MAYUR mascot.
 * Keeps all mutable state inside a single object so frames update smoothly with zero recomposition churn.
 */
class Mayur3DRigController(
    initialYawDeg: Float = 16f,
    initialPitchDeg: Float = 8f
) {
    // Orbit camera state + angular inertia
    var orbitYawDeg: Float = initialYawDeg
        private set
    var orbitPitchDeg: Float = initialPitchDeg.coerceIn(MIN_PITCH_DEG, MAX_PITCH_DEG)
        private set
    var angularVelYaw: Float = 0f
        private set
    var angularVelPitch: Float = 0f
        private set
    private var isDragging: Boolean = false
    private var lastTotalYawDeg: Float = initialYawDeg

    // Continuous simulation clocks
    private var timeSec: Float = 0f
    private var danceClockSec: Float = 0f
    private var danceBlend: Float = 0f
    private var sleepBlend: Float = 0f

    // Natural blink timer
    private var nextBlinkAtSec: Float = 2.6f
    private var blinkTimerSec: Float = -1f
    private var doubleBlinkPending: Boolean = false

    // Organic idle behavior scheduler
    private var currentIdleAction: MayurIdleAction = MayurIdleAction.NONE
    private var idleActionRemainingSec: Float = 0f
    private var idleActionDurationSec: Float = 1f
    private var nextIdleActionAtSec: Float = 3.2f
    private var idleCycleIndex: Int = 0
    private var idleLookTargetYaw: Float = 0f
    private var idleLookTargetPitch: Float = 0f

    // Active touch reaction state
    private var activeReaction: MayurReactionEvent? = null
    private var reactionElapsedSec: Float = 0f
    private var reactionLookYaw: Float = 0f
    private var reactionLookPitch: Float = 0f
    private var touchOrbitNudgeYaw: Float = 0f

    // Smoothly interpolated skeletal channels
    private var smoothedHeadYaw: Float = 0f
    private var smoothedHeadPitch: Float = 0f
    private var smoothedHeadRoll: Float = 0f
    private var smoothedEyeX: Float = 0f
    private var smoothedEyeY: Float = 0f
    private var smoothedTailSpread: Float = 0.62f
    private var tailLagYaw: Float = 0f
    private var tailLagVel: Float = 0f
    private var crownLagPitch: Float = 0f
    private var crownLagRoll: Float = 0f
    private var smoothedBeatPulse: Float = 0f
    private var smoothedWaveformGlow: Float = 0.35f

    var currentPose: MayurBonePose = MayurBonePose(orbitYawDeg = initialYawDeg, orbitPitchDeg = initialPitchDeg)
        private set

    fun beginDrag() {
        isDragging = true
        angularVelYaw = 0f
        angularVelPitch = 0f
        touchOrbitNudgeYaw = 0f
    }

    /**
     * Applies user touch drag deltas (in degrees) for silky 360° yaw & vertical pitch control.
     */
    fun applyDragDelta(deltaYawDeg: Float, deltaPitchDeg: Float, dtSeconds: Float) {
        orbitYawDeg = Mayur3DMath.wrapAngle360(orbitYawDeg + deltaYawDeg)
        orbitPitchDeg = (orbitPitchDeg + deltaPitchDeg).coerceIn(MIN_PITCH_DEG, MAX_PITCH_DEG)

        val safeDt = dtSeconds.coerceIn(0.008f, 0.06f)
        val instVelYaw = (deltaYawDeg / safeDt).coerceIn(-560f, 560f)
        val instVelPitch = (deltaPitchDeg / safeDt).coerceIn(-280f, 280f)
        angularVelYaw = angularVelYaw * 0.45f + instVelYaw * 0.55f
        angularVelPitch = angularVelPitch * 0.45f + instVelPitch * 0.55f
    }

    fun endDrag() {
        isDragging = false
    }

    fun triggerReaction(event: MayurReactionEvent) {
        activeReaction = event
        reactionElapsedSec = 0f
        currentIdleAction = MayurIdleAction.NONE
        idleActionRemainingSec = 0f

        reactionLookYaw = (event.touchNormalizedX * 34f).coerceIn(-36f, 36f)
        reactionLookPitch = (-event.touchNormalizedY * 18f).coerceIn(-18f, 18f)

        // When user taps the body, gently rotate Mayur toward the touch point
        if (event.type == MayurReactionType.TURN_TO_TOUCH) {
            val signedOrbit = Mayur3DMath.wrapAngleSigned(orbitYawDeg)
            // Turn partially toward front + side of tap
            val desiredYaw = (event.touchNormalizedX * 26f).coerceIn(-32f, 32f)
            touchOrbitNudgeYaw = Mayur3DMath.wrapAngleSigned(desiredYaw - signedOrbit).coerceIn(-75f, 75f)
        } else if (event.type == MayurReactionType.TAIL_FAN_SHAKE) {
            // Subtle glance back toward tail
            reactionLookYaw = if (Mayur3DMath.wrapAngleSigned(orbitYawDeg) >= 0f) -26f else 26f
        }
    }

    /**
     * Advances skeletal physics, orbit inertia, dance choreography, idle actions, and follow-through.
     */
    fun step(
        dtSeconds: Float,
        isDancing: Boolean,
        danceStyle: MayurDanceStyle,
        danceIntensity: MayurDanceIntensity,
        telemetry: MayurAudioTelemetry,
        isSleeping: Boolean,
        reduceAnimations: Boolean
    ): MayurBonePose {
        val dt = dtSeconds.coerceIn(0.004f, 0.064f)
        timeSec += dt

        // 1. Orbit inertia & soft nudge toward touch
        if (!isDragging) {
            if (abs(angularVelYaw) > 0.4f) {
                orbitYawDeg = Mayur3DMath.wrapAngle360(orbitYawDeg + angularVelYaw * dt)
                angularVelYaw *= exp(-4.4f * dt)
            } else {
                angularVelYaw = 0f
            }

            if (abs(angularVelPitch) > 0.4f) {
                val nextPitch = orbitPitchDeg + angularVelPitch * dt
                if (nextPitch <= MIN_PITCH_DEG || nextPitch >= MAX_PITCH_DEG) {
                    orbitPitchDeg = nextPitch.coerceIn(MIN_PITCH_DEG, MAX_PITCH_DEG)
                    angularVelPitch *= -0.18f // soft cushion stop at tilt limits
                } else {
                    orbitPitchDeg = nextPitch
                    angularVelPitch *= exp(-5.2f * dt)
                }
            } else {
                angularVelPitch = 0f
            }

            if (abs(touchOrbitNudgeYaw) > 0.3f) {
                val stepYaw = touchOrbitNudgeYaw * (1f - exp(-5.5f * dt))
                orbitYawDeg = Mayur3DMath.wrapAngle360(orbitYawDeg + stepYaw)
                touchOrbitNudgeYaw -= stepYaw
            } else {
                touchOrbitNudgeYaw = 0f
            }
        }

        // 2. Smooth mode blending (dance <-> idle, awake <-> sleeping)
        val targetDanceBlend = if (isDancing && !isSleeping && !reduceAnimations) 1f else 0f
        danceBlend += (targetDanceBlend - danceBlend) * (1f - exp(-4.2f * dt))

        val targetSleepBlend = if (isSleeping) 1f else 0f
        sleepBlend += (targetSleepBlend - sleepBlend) * (1f - exp(-3.5f * dt))

        // Beat pulse decay & boost
        val targetBeat = if (isDancing) telemetry.beatPulse else 0f
        smoothedBeatPulse = if (targetBeat > smoothedBeatPulse) {
            smoothedBeatPulse + (targetBeat - smoothedBeatPulse) * (1f - exp(-18f * dt))
        } else {
            smoothedBeatPulse * exp(-6.8f * dt)
        }

        // Advance active reaction
        var reactionProgress = 0f
        val reaction = activeReaction
        if (reaction != null) {
            reactionElapsedSec += dt
            val durationSec = (reaction.durationMs / 1000f).coerceAtLeast(0.4f)
            reactionProgress = (reactionElapsedSec / durationSec).coerceIn(0f, 1f)
            if (reactionElapsedSec >= durationSec) {
                activeReaction = null
                reactionProgress = 0f
            }
        }

        // 3. Natural blinking (suppressed into gentle closed lids when sleeping)
        var blinkAmount = 0f
        if (!reduceAnimations && sleepBlend < 0.7f) {
            if (blinkTimerSec >= 0f) {
                blinkTimerSec += dt
                val blinkDur = 0.17f
                if (blinkTimerSec >= blinkDur) {
                    blinkTimerSec = -1f
                    if (doubleBlinkPending) {
                        doubleBlinkPending = false
                        nextBlinkAtSec = timeSec + 0.14f
                    } else {
                        nextBlinkAtSec = timeSec + 2.4f + ((idleCycleIndex * 1.13f) % 2.2f)
                    }
                } else {
                    blinkAmount = sin((blinkTimerSec / blinkDur) * PI.toFloat()).coerceIn(0f, 1f)
                }
            } else if (timeSec >= nextBlinkAtSec) {
                blinkTimerSec = 0f
                doubleBlinkPending = (idleCycleIndex % 4 == 2)
            }
        }
        // Sleepy eyelids smoothly close as sleepBlend approaches 1
        blinkAmount = maxOf(blinkAmount, sleepBlend * 0.92f)

        // 4. Breathing cycle
        val breathRate = if (isSleeping) 1.45f else 2.25f
        val breathWave = if (reduceAnimations) 0f else sin(timeSec * breathRate)
        val breathScale = 1f + breathWave * (if (isSleeping) 0.022f else 0.018f)

        // 5. Organic Idle Behavior Scheduler (only when awake, not dancing, no active reaction)
        if (!reduceAnimations && danceBlend < 0.15f && sleepBlend < 0.35f && reaction == null && !isDragging) {
            if (idleActionRemainingSec > 0f) {
                idleActionRemainingSec -= dt
                if (idleActionRemainingSec <= 0f) {
                    currentIdleAction = MayurIdleAction.NONE
                    nextIdleActionAtSec = timeSec + 2.6f + ((idleCycleIndex * 0.9f) % 2.0f)
                }
            } else if (timeSec >= nextIdleActionAtSec) {
                idleCycleIndex++
                currentIdleAction = when (idleCycleIndex % 5) {
                    0 -> MayurIdleAction.LOOK_AROUND
                    1 -> MayurIdleAction.ADJUST_HEADPHONES
                    2 -> MayurIdleAction.PLAY_WITH_TAIL
                    3 -> MayurIdleAction.PLAYFUL_HOP
                    else -> MayurIdleAction.LOOK_AROUND
                }
                idleActionDurationSec = when (currentIdleAction) {
                    MayurIdleAction.LOOK_AROUND -> 2.1f
                    MayurIdleAction.ADJUST_HEADPHONES -> 1.55f
                    MayurIdleAction.PLAY_WITH_TAIL -> 1.85f
                    MayurIdleAction.PLAYFUL_HOP -> 0.95f
                    else -> 1.4f
                }
                idleActionRemainingSec = idleActionDurationSec
                idleLookTargetYaw = if (idleCycleIndex % 2 == 0) 24f else -24f
                idleLookTargetPitch = if (idleCycleIndex % 3 == 0) 7f else -4f
            }
        } else if (danceBlend >= 0.25f || sleepBlend >= 0.5f) {
            currentIdleAction = MayurIdleAction.NONE
            idleActionRemainingSec = 0f
        }

        // Accumulate base skeletal offsets
        var bodyYaw = 0f
        var bodyPitch = sleepBlend * 11f
        var bodyRoll = sleepBlend * -5f
        var bodyHopY = -sleepBlend * 0.06f
        var bodyShiftX = 0f

        var targetHeadYaw = 0f
        var targetHeadPitch = sleepBlend * 14f
        var targetHeadRoll = sleepBlend * -8f
        var targetEyeX = 0f
        var targetEyeY = 0f

        var smileAmount = if (isSleeping) 0.38f else 0.28f
        var beakOpen = 0f
        var blushAlpha = 0.26f

        var leftWingFlap = 0f
        var leftWingPitch = 0f
        var rightWingFlap = 0f
        var rightWingPitch = 0f

        var leftFootLift = 0f
        var rightFootLift = 0f
        var leftFootForward = 0f
        var rightFootForward = 0f

        var targetTailSpread = 0.62f - sleepBlend * 0.16f
        var tailLiftDeg = 18f - sleepBlend * 8f
        var tailShakeAmp = 0f

        // 5a. Over-the-shoulder look-back when user views Mayur from behind!
        val signedOrbit = Mayur3DMath.wrapAngleSigned(orbitYawDeg)
        if (sleepBlend < 0.5f && danceBlend < 0.35f && reaction == null && abs(signedOrbit) in 108f..172f) {
            val lookBackStrength = ((abs(signedOrbit) - 108f) / 42f).coerceIn(0f, 1f)
            val shoulderSign = if (signedOrbit >= 0f) -1f else 1f
            targetHeadYaw += shoulderSign * 46f * lookBackStrength
            targetHeadRoll += shoulderSign * 6f * lookBackStrength
            targetEyeX = shoulderSign * 0.55f * lookBackStrength
            smileAmount = maxOf(smileAmount, 0.42f * lookBackStrength)
        }

        // 5b. Apply idle action channels
        if (currentIdleAction != MayurIdleAction.NONE && idleActionDurationSec > 0f) {
            val p = (1f - (idleActionRemainingSec / idleActionDurationSec)).coerceIn(0f, 1f)
            val env = sin(p * PI.toFloat())
            when (currentIdleAction) {
                MayurIdleAction.LOOK_AROUND -> {
                    targetHeadYaw += idleLookTargetYaw * env
                    targetHeadPitch += idleLookTargetPitch * env
                    targetEyeX = (idleLookTargetYaw / 30f).coerceIn(-0.7f, 0.7f) * env
                }
                MayurIdleAction.ADJUST_HEADPHONES -> {
                    // Right wing lifts gracefully toward the golden headphone cup with a slight head tilt
                    rightWingFlap += 38f * env
                    rightWingPitch += 28f * env + sin(p * 4f * PI.toFloat()) * 6f * env
                    targetHeadRoll += -10f * env
                    targetHeadYaw += 8f * env
                    smileAmount = maxOf(smileAmount, 0.48f * env)
                }
                MayurIdleAction.PLAY_WITH_TAIL -> {
                    targetHeadYaw += -30f * env
                    targetHeadPitch += -6f * env
                    bodyYaw += -10f * env
                    targetTailSpread += 0.24f * env
                    tailShakeAmp += 5.5f * env
                }
                MayurIdleAction.PLAYFUL_HOP -> {
                    val hopArc = sin(p * PI.toFloat())
                    bodyHopY += hopArc * 0.16f
                    leftWingFlap += hopArc * 26f
                    rightWingFlap += hopArc * 26f
                    leftFootLift += hopArc * 0.08f
                    rightFootLift += hopArc * 0.08f
                    smileAmount = maxOf(smileAmount, 0.72f * hopArc)
                    beakOpen = maxOf(beakOpen, 0.35f * hopArc)
                }
                else -> Unit
            }
        }

        // 6. Music-Reactive 3D Dancing (7 dedicated styles synchronized to real audio energy & beat)
        if (danceBlend > 0.01f) {
            val amp = (0.35f + telemetry.overallAmplitude * 0.95f) * danceIntensity.amplitudeMultiplier
            val beat = smoothedBeatPulse * danceIntensity.amplitudeMultiplier
            val speed = danceIntensity.speedMultiplier * when (danceStyle) {
                MayurDanceStyle.POP -> 5.6f
                MayurDanceStyle.CLASSICAL -> 3.0f
                MayurDanceStyle.ELECTRONIC -> 6.8f
                MayurDanceStyle.HIP_HOP -> 4.8f
                MayurDanceStyle.LO_FI -> 2.6f
                MayurDanceStyle.ROMANTIC -> 2.1f
                MayurDanceStyle.HIGH_ENERGY -> 7.6f
            }
            danceClockSec += dt * speed
            val w = danceClockSec
            val b = danceBlend

            when (danceStyle) {
                MayurDanceStyle.POP -> {
                    // Energetic steps, lively head bobs & upbeat side turns
                    val stepWave = sin(w)
                    bodyHopY += (abs(sin(w)) * 0.11f * amp + beat * 0.09f) * b
                    bodyYaw += sin(w * 0.5f) * 20f * amp * b
                    bodyRoll += stepWave * 6.5f * amp * b
                    targetHeadPitch += (sin(w * 2f) * 8f * amp + beat * 8f) * b
                    targetHeadYaw += cos(w * 0.5f) * 14f * amp * b
                    leftWingFlap += (14f + abs(sin(w)) * 20f * amp) * b
                    rightWingFlap += (14f + abs(cos(w)) * 20f * amp) * b
                    leftFootLift += stepWave.coerceAtLeast(0f) * 0.11f * amp * b
                    rightFootLift += (-stepWave).coerceAtLeast(0f) * 0.11f * amp * b
                    leftFootForward += stepWave * 0.07f * amp * b
                    rightFootForward += -stepWave * 0.07f * amp * b
                    targetTailSpread += (0.14f + beat * 0.14f) * b
                    smileAmount = maxOf(smileAmount, 0.72f * b)
                }
                MayurDanceStyle.CLASSICAL -> {
                    // Graceful figure-8 arcs, elegant 360° turns & majestic tail display
                    bodyYaw += sin(w * 0.45f) * 44f * amp * b
                    bodyShiftX += sin(w * 0.5f) * 0.09f * amp * b
                    bodyHopY += (sin(w) * 0.045f + 0.04f) * amp * b
                    bodyRoll += sin(w * 0.5f) * 8f * amp * b
                    targetHeadYaw += -sin(w * 0.45f) * 18f * amp * b
                    targetHeadRoll += cos(w * 0.5f) * 9f * amp * b
                    leftWingFlap += (22f + sin(w) * 18f * amp) * b
                    rightWingFlap += (22f - sin(w) * 18f * amp) * b
                    leftFootLift += sin(w).coerceAtLeast(0f) * 0.06f * amp * b
                    rightFootLift += (-sin(w)).coerceAtLeast(0f) * 0.06f * amp * b
                    targetTailSpread += (0.34f + sin(w) * 0.08f) * b
                    tailLiftDeg += 8f * b
                    smileAmount = maxOf(smileAmount, 0.60f * b)
                }
                MayurDanceStyle.ELECTRONIC -> {
                    // Beat-locked rhythmic bouncing & glowing waveform pulses
                    val bounce = abs(sin(w)) * 0.14f * amp + beat * 0.14f
                    bodyHopY += bounce * b
                    bodyPitch += (sin(w * 2f) * 5f + beat * 6f) * b
                    bodyYaw += sin(w * 0.5f) * 14f * amp * b
                    targetHeadPitch += (beat * 13f + sin(w * 2f) * 7f * amp) * b
                    leftWingFlap += (12f + beat * 28f) * b
                    rightWingFlap += (12f + beat * 28f) * b
                    leftFootLift += (bounce * 0.45f) * b
                    rightFootLift += (bounce * 0.45f) * b
                    targetTailSpread += (0.18f + beat * 0.22f) * b
                    tailShakeAmp += (3.5f + beat * 6f) * b
                    smileAmount = maxOf(smileAmount, 0.76f * b)
                }
                MayurDanceStyle.HIP_HOP -> {
                    // Playful confident body lean, rhythmic shoulder drops & swagger head nods
                    val groove = sin(w)
                    bodyPitch += (-6f + beat * 8f) * b
                    bodyRoll += groove * 10.5f * amp * b
                    bodyYaw += (groove * 18f + 8f) * amp * b
                    bodyHopY += (abs(groove) * 0.06f + beat * 0.07f) * amp * b
                    targetHeadPitch += (beat * 12f + sin(w * 2f) * 6f) * b
                    targetHeadRoll += -groove * 7f * amp * b
                    leftWingFlap += (18f + groove.coerceAtLeast(0f) * 22f * amp) * b
                    rightWingFlap += (18f + (-groove).coerceAtLeast(0f) * 22f * amp) * b
                    leftFootForward += groove * 0.09f * amp * b
                    rightFootForward += -groove * 0.09f * amp * b
                    targetTailSpread += 0.12f * b
                    smileAmount = maxOf(smileAmount, 0.55f * b)
                }
                MayurDanceStyle.LO_FI -> {
                    // Relaxed side-to-side swaying & gentle hypnotic head nods
                    val sway = sin(w)
                    bodyRoll += sway * 5.2f * amp * b
                    bodyShiftX += sway * 0.045f * amp * b
                    targetHeadPitch += (sin(w * 2f) * 6.5f * amp + beat * 4f) * b
                    targetHeadRoll += sway * 4.5f * amp * b
                    leftWingFlap += (4f + abs(sway) * 6f) * b
                    rightWingFlap += (4f + abs(sway) * 6f) * b
                    smileAmount = maxOf(smileAmount, 0.46f * b)
                }
                MayurDanceStyle.ROMANTIC -> {
                    // Slow graceful floating sway & tender head tilt
                    val sway = sin(w)
                    bodyRoll += sway * 6.2f * amp * b
                    bodyYaw += sin(w * 0.5f) * 14f * amp * b
                    bodyHopY += (cos(w) * 0.03f + 0.02f) * amp * b
                    targetHeadRoll += cos(w) * 7.5f * amp * b
                    targetHeadPitch += sin(w) * 4f * amp * b
                    leftWingFlap += (10f + abs(sway) * 12f * amp) * b
                    rightWingFlap += (10f + abs(sway) * 12f * amp) * b
                    targetTailSpread += 0.22f * b
                    smileAmount = maxOf(smileAmount, 0.56f * b)
                    blushAlpha = maxOf(blushAlpha, 0.42f * b)
                }
                MayurDanceStyle.HIGH_ENERGY -> {
                    // Faster footwork, energetic hops, dynamic turns & vibrant tail rhythm
                    val step = sin(w)
                    bodyHopY += (abs(step) * 0.15f * amp + beat * 0.13f) * b
                    bodyYaw += sin(w * 0.5f) * 32f * amp * b
                    bodyRoll += step * 8.5f * amp * b
                    targetHeadPitch += (sin(w * 2f) * 10f * amp + beat * 10f) * b
                    targetHeadYaw += cos(w) * 16f * amp * b
                    leftWingFlap += (22f + abs(step) * 28f * amp) * b
                    rightWingFlap += (22f + abs(cos(w)) * 28f * amp) * b
                    leftFootLift += step.coerceAtLeast(0f) * 0.15f * amp * b
                    rightFootLift += (-step).coerceAtLeast(0f) * 0.15f * amp * b
                    leftFootForward += step * 0.10f * amp * b
                    rightFootForward += -step * 0.10f * amp * b
                    targetTailSpread += (0.36f + beat * 0.16f) * b
                    tailShakeAmp += (6.5f + beat * 7f) * b
                    smileAmount = maxOf(smileAmount, 0.85f * b)
                    beakOpen = maxOf(beakOpen, (beat * 0.55f) * b)
                }
            }
        }

        // 7. Apply active touch reaction overlay
        if (reaction != null) {
            val env = sin(reactionProgress * PI.toFloat()).coerceIn(0f, 1f)
            when (reaction.type) {
                MayurReactionType.HEAD_PAT_HAPPY -> {
                    // Blink / joyful squint smile + happy head tilt
                    blinkAmount = maxOf(blinkAmount, (sin(reactionProgress * 2f * PI.toFloat()).coerceAtLeast(0f)) * 0.78f)
                    smileAmount = maxOf(smileAmount, 0.95f * env)
                    blushAlpha = maxOf(blushAlpha, 0.55f * env)
                    targetHeadRoll += (if (reaction.variantIndex % 2 == 0) 12f else -12f) * env
                    targetHeadPitch += 7f * env
                    bodyHopY += sin(reactionProgress * PI.toFloat()) * 0.08f
                    beakOpen = maxOf(beakOpen, 0.35f * env)
                }
                MayurReactionType.TURN_TO_TOUCH -> {
                    targetHeadYaw += reactionLookYaw * env
                    targetHeadPitch += reactionLookPitch * env
                    targetEyeX = (reactionLookYaw / 34f).coerceIn(-0.85f, 0.85f) * env
                    targetEyeY = (reactionLookPitch / 18f).coerceIn(-0.6f, 0.6f) * env
                    smileAmount = maxOf(smileAmount, 0.65f * env)
                    bodyHopY += sin(reactionProgress * PI.toFloat()) * 0.05f
                }
                MayurReactionType.ADJUST_HEADPHONES -> {
                    rightWingFlap += 44f * env
                    rightWingPitch += 32f * env
                    targetHeadRoll += sin(reactionProgress * 3f * PI.toFloat()) * 11f * env
                    smileAmount = maxOf(smileAmount, 0.70f * env)
                }
                MayurReactionType.TAIL_FAN_SHAKE -> {
                    targetTailSpread = maxOf(targetTailSpread, 0.62f + 0.52f * env)
                    tailLiftDeg += 14f * env
                    tailShakeAmp += 11f * env
                    targetHeadYaw += reactionLookYaw * env
                    bodyRoll += sin(reactionProgress * 4f * PI.toFloat()) * 5f * env
                    smileAmount = maxOf(smileAmount, 0.80f * env)
                }
                MayurReactionType.DOUBLE_TAP_HAPPY -> {
                    // Special double-tap joyful celebration: double hop + radiant fan + excited chirp
                    val doubleHop = abs(sin(reactionProgress * 2f * PI.toFloat()))
                    bodyHopY += doubleHop * 0.22f
                    bodyYaw += sin(reactionProgress * 2f * PI.toFloat()) * 24f
                    leftWingFlap += 42f * doubleHop
                    rightWingFlap += 42f * doubleHop
                    leftFootLift += doubleHop * 0.12f
                    rightFootLift += doubleHop * 0.12f
                    targetTailSpread = maxOf(targetTailSpread, 1.12f * env + 0.62f * (1f - env))
                    smileAmount = 1f
                    beakOpen = 0.65f * env
                    blushAlpha = 0.58f * env
                }
                MayurReactionType.SPECIAL_LONG_PRESS -> {
                    // Playful full 360° twirl & wing flutter performance
                    val spin = (1f - cos(reactionProgress * PI.toFloat())) * 0.5f * 360f
                    bodyYaw += spin
                    bodyHopY += sin(reactionProgress * PI.toFloat()) * 0.18f
                    val flutter = abs(sin(reactionProgress * 5f * PI.toFloat()))
                    leftWingFlap += 24f + flutter * 32f * env
                    rightWingFlap += 24f + flutter * 32f * env
                    targetTailSpread = maxOf(targetTailSpread, 1.08f * env + 0.62f * (1f - env))
                    smileAmount = maxOf(smileAmount, 0.92f * env)
                    beakOpen = maxOf(beakOpen, 0.45f * env)
                }
                MayurReactionType.SLEEPY_WAKE_IRRITATED -> {
                    // Wakes groggily with half-closed eyes, shakes head slightly, then drifts back to sleep
                    blinkAmount = (0.45f + (reactionProgress * 0.5f)).coerceIn(0.4f, 0.92f)
                    targetHeadRoll += sin(reactionProgress * 3f * PI.toFloat()) * 10f * env
                    targetHeadYaw += sin(reactionProgress * 2f * PI.toFloat()) * 14f * env
                    smileAmount = 0.05f
                }
            }
        }

        // 8. Smooth skeletal interpolation & secondary spring-damper follow-through
        val headLerp = 1f - exp(-9.5f * dt)
        smoothedHeadYaw += (targetHeadYaw - smoothedHeadYaw) * headLerp
        smoothedHeadPitch += (targetHeadPitch - smoothedHeadPitch) * headLerp
        smoothedHeadRoll += (targetHeadRoll - smoothedHeadRoll) * headLerp
        smoothedEyeX += (targetEyeX - smoothedEyeX) * (1f - exp(-12f * dt))
        smoothedEyeY += (targetEyeY - smoothedEyeY) * (1f - exp(-12f * dt))
        smoothedTailSpread += (targetTailSpread - smoothedTailSpread) * (1f - exp(-7.5f * dt))

        // Tail spring-damper lags naturally behind total yaw changes
        val totalYaw = orbitYawDeg + bodyYaw
        val deltaYaw = Mayur3DMath.wrapAngleSigned(totalYaw - lastTotalYawDeg)
        lastTotalYawDeg = totalYaw
        tailLagVel -= (deltaYaw / dt).coerceIn(-360f, 360f) * 0.18f
        val springForce = -42f * tailLagYaw - 8.5f * tailLagVel
        tailLagVel += springForce * dt
        tailLagYaw = (tailLagYaw + tailLagVel * dt).coerceIn(-28f, 28f)

        // Crown feather secondary inertia
        val crownTargetPitch = -bodyHopY * 32f - smoothedHeadPitch * 0.25f
        val crownTargetRoll = -bodyRoll * 0.45f - smoothedHeadRoll * 0.35f
        crownLagPitch += (crownTargetPitch - crownLagPitch) * (1f - exp(-7f * dt))
        crownLagRoll += (crownTargetRoll - crownLagRoll) * (1f - exp(-7f * dt))

        // Jacket waveform emblem glow
        val targetWaveformGlow = when {
            isSleeping -> 0.14f
            reaction?.type == MayurReactionType.DOUBLE_TAP_HAPPY ||
                reaction?.type == MayurReactionType.SPECIAL_LONG_PRESS -> 1.0f
            isDancing && danceStyle == MayurDanceStyle.ELECTRONIC ->
                (0.55f + smoothedBeatPulse * 0.45f + telemetry.overallAmplitude * 0.25f).coerceIn(0.45f, 1f)
            isDancing ->
                (0.42f + telemetry.overallAmplitude * 0.48f + smoothedBeatPulse * 0.22f).coerceIn(0.35f, 1f)
            else -> 0.32f + sin(timeSec * 2.1f) * 0.08f
        }
        smoothedWaveformGlow += (targetWaveformGlow - smoothedWaveformGlow) * (1f - exp(-10f * dt))

        val expression = when {
            reaction != null -> reaction.expression
            isSleeping -> MayurExpression.SLEEPY
            isDancing -> if (danceStyle == MayurDanceStyle.HIGH_ENERGY || danceStyle == MayurDanceStyle.ELECTRONIC) {
                MayurExpression.EXCITED
            } else {
                MayurExpression.HAPPY
            }
            currentIdleAction == MayurIdleAction.LOOK_AROUND -> MayurExpression.CURIOUS
            currentIdleAction == MayurIdleAction.PLAYFUL_HOP -> MayurExpression.HAPPY
            else -> MayurExpression.RELAXED
        }

        currentPose = MayurBonePose(
            orbitYawDeg = orbitYawDeg,
            orbitPitchDeg = orbitPitchDeg,
            bodyYawDeg = bodyYaw,
            bodyPitchDeg = bodyPitch,
            bodyRollDeg = bodyRoll,
            bodyHopY = bodyHopY,
            bodyShiftX = bodyShiftX,
            breathScale = breathScale,
            headYawDeg = smoothedHeadYaw,
            headPitchDeg = smoothedHeadPitch,
            headRollDeg = smoothedHeadRoll,
            eyeLookX = smoothedEyeX,
            eyeLookY = smoothedEyeY,
            blinkAmount = blinkAmount.coerceIn(0f, 1f),
            smileAmount = smileAmount.coerceIn(0f, 1f),
            beakOpenAmount = beakOpen.coerceIn(0f, 1f),
            blushAlpha = blushAlpha.coerceIn(0f, 0.7f),
            leftWingFlapDeg = leftWingFlap,
            leftWingPitchDeg = leftWingPitch,
            rightWingFlapDeg = rightWingFlap,
            rightWingPitchDeg = rightWingPitch,
            leftFootLift = leftFootLift,
            rightFootLift = rightFootLift,
            leftFootForward = leftFootForward,
            rightFootForward = rightFootForward,
            tailSpread = smoothedTailSpread.coerceIn(0.35f, 1.18f),
            tailLiftDeg = tailLiftDeg,
            tailLagYawDeg = tailLagYaw,
            tailShakePhase = timeSec * 15f,
            tailShakeAmp = tailShakeAmp,
            crownLagPitchDeg = crownLagPitch,
            crownLagRollDeg = crownLagRoll,
            waveformGlow = smoothedWaveformGlow.coerceIn(0.1f, 1f),
            beatPulse = smoothedBeatPulse.coerceIn(0f, 1f),
            audioAmplitude = telemetry.overallAmplitude.coerceIn(0f, 1f),
            elapsedTimeSec = timeSec,
            isDancing = isDancing && !isSleeping,
            isSleeping = isSleeping,
            expression = expression,
            activeReactionType = reaction?.type,
            reactionProgress = reactionProgress
        )
        return currentPose
    }

    companion object {
        const val MIN_PITCH_DEG = -28f
        const val MAX_PITCH_DEG = 36f
    }
}
