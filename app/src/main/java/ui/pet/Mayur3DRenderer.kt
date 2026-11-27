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
import domain.pet.HitZoneProjection
import domain.pet.Mayur3DMath
import domain.pet.MayurAnimationQuality
import domain.pet.MayurBonePose
import domain.pet.MayurCostume
import domain.pet.MayurTouchZone
import domain.pet.ProjectedPoint
import domain.pet.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Genuine 3D procedural skeletal renderer for the redesigned MAYUR peacock mascot.
 *
 * Uses hierarchical 3D bone transforms, perspective camera projection (full 360° yaw + pitch),
 * per-frame back-to-front Z-depth sorting, studio directional + rim lighting, fluffy feather shading,
 * expressive turquoise-blue eyes, black-and-gold MAYUR headphones, and an audio-reactive glowing
 * waveform jacket emblem — all with zero dependency on any static PNG image.
 */
class Mayur3DRenderer {

    private class DrawCommand(
        var camZ: Float,
        var draw: DrawScope.() -> Unit
    )

    private val drawQueue = ArrayList<DrawCommand>(64)

    fun render(
        scope: DrawScope,
        pose: MayurBonePose,
        costume: MayurCostume,
        quality: MayurAnimationQuality,
        outHitZones: MutableList<HitZoneProjection>
    ) {
        val w = scope.size.width
        val h = scope.size.height
        if (w <= 1f || h <= 1f) return

        drawQueue.clear()
        outHitZones.clear()

        // 1. Soft environment aura behind character
        Mayur3DCostumeOverlay.drawBackdropAura(scope, costume, pose, quality)

        // Bone transform helpers
        fun bodyToWorld(local: Vec3): Vec3 {
            val scaled = Vec3(
                local.x * pose.breathScale,
                local.y * pose.breathScale,
                local.z * pose.breathScale
            )
            val rot = Mayur3DMath.rotateEuler(scaled, pose.bodyPitchDeg, pose.bodyYawDeg, pose.bodyRollDeg)
            return Vec3(rot.x + pose.bodyShiftX, rot.y + pose.bodyHopY, rot.z)
        }

        fun bodyDirToView(dir: Vec3): Vec3 {
            val worldDir = Mayur3DMath.rotateEuler(dir, pose.bodyPitchDeg, pose.bodyYawDeg, pose.bodyRollDeg)
            return Mayur3DMath.worldToView(worldDir, pose.orbitYawDeg, pose.orbitPitchDeg).normalized()
        }

        val neckPivotBody = Vec3(0f, 0.36f, 0.03f)
        fun headToWorld(headLocal: Vec3): Vec3 {
            val headRot = Mayur3DMath.rotateEuler(headLocal, pose.headPitchDeg, pose.headYawDeg, pose.headRollDeg)
            val inBody = neckPivotBody + headRot
            return bodyToWorld(inBody)
        }

        fun headDirToView(dir: Vec3): Vec3 {
            val headDir = Mayur3DMath.rotateEuler(dir, pose.headPitchDeg, pose.headYawDeg, pose.headRollDeg)
            return bodyDirToView(headDir)
        }

        fun project(world: Vec3): ProjectedPoint =
            Mayur3DMath.projectWorld(world, pose.orbitYawDeg, pose.orbitPitchDeg, w, h)

        // 2. Ground contact shadow (always beneath feet)
        val shadowCenter = project(Vec3(pose.bodyShiftX, -0.66f, 0f))
        val hopShadowFade = (1f - pose.bodyHopY * 1.4f).coerceIn(0.42f, 1f)
        scope.drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x55081020).copy(alpha = 0.42f * hopShadowFade),
                    Color.Transparent
                ),
                center = Offset(shadowCenter.sx, shadowCenter.sy),
                radius = shadowCenter.scale * 0.62f * hopShadowFade
            ),
            topLeft = Offset(
                shadowCenter.sx - shadowCenter.scale * 0.60f * hopShadowFade,
                shadowCenter.sy - shadowCenter.scale * 0.17f * hopShadowFade
            ),
            size = Size(
                shadowCenter.scale * 1.20f * hopShadowFade,
                shadowCenter.scale * 0.34f * hopShadowFade
            )
        )

        // 3. Articulated 3D Peacock Tail Feathers (5 / 7 / 9 feathers with spring follow-through)
        val featherCount = quality.tailFeatherCount
        val tailPivotBody = Vec3(0f, -0.18f, -0.30f)
        var tailHitSumX = 0f
        var tailHitSumY = 0f
        var tailHitMaxZ = Float.NEGATIVE_INFINITY

        for (i in 0 until featherCount) {
            val t = if (featherCount <= 1) 0f else (i / (featherCount - 1f)) * 2f - 1f // -1..+1
            val fanAngleDeg = t * 62f * pose.tailSpread
            val elevationDeg = pose.tailLiftDeg + (1f - abs(t)) * 34f
            val shakeOffset = sin(pose.tailShakePhase + i * 0.9f) * pose.tailShakeAmp
            val length = 0.96f - abs(t) * 0.14f

            // Build feather spine (base -> mid -> tip) in local tail fan space
            val midLocal = Vec3(0f, length * 0.48f, -length * 0.42f)
            val tipLocal = Vec3(0f, length * 0.92f, -length * 0.64f)

            fun transformTailPt(pt: Vec3): Vec3 {
                val pitched = Mayur3DMath.rotateX(pt, -elevationDeg * 0.35f)
                val fanned = Mayur3DMath.rotateZ(pitched, fanAngleDeg + shakeOffset * 0.6f)
                val lagged = Mayur3DMath.rotateY(fanned, pose.tailLagYawDeg + shakeOffset * 0.45f)
                return bodyToWorld(tailPivotBody + lagged)
            }

            val baseW = bodyToWorld(tailPivotBody)
            val midW = transformTailPt(midLocal)
            val tipW = transformTailPt(tipLocal)

            val baseP = project(baseW)
            val midP = project(midW)
            val tipP = project(tipW)

            tailHitSumX += tipP.sx
            tailHitSumY += tipP.sy
            if (tipP.camZ > tailHitMaxZ) tailHitMaxZ = tipP.camZ

            // Depth-sort using weighted midpoint/tip depth so back view puts tail cleanly in front
            val sortZ = midP.camZ * 0.45f + tipP.camZ * 0.55f
            val featherIndex = i
            drawQueue.add(
                DrawCommand(sortZ) {
                    drawPeacockTailFeather(
                        base = baseP,
                        mid = midP,
                        tip = tipP,
                        featherIndex = featherIndex,
                        quality = quality
                    )
                }
            )
        }
        if (featherCount > 0) {
            outHitZones.add(
                HitZoneProjection(
                    zone = MayurTouchZone.TAIL,
                    screenX = tailHitSumX / featherCount,
                    screenY = tailHitSumY / featherCount,
                    radiusPx = shadowCenter.scale * 0.58f,
                    camZ = tailHitMaxZ
                )
            )
        }

        // 4. Articulated 3D Golden-Orange Feet (stepping & hopping)
        val leftFootWorld = bodyToWorld(Vec3(-0.15f, -0.56f + pose.leftFootLift, 0.08f + pose.leftFootForward))
        val rightFootWorld = bodyToWorld(Vec3(0.15f, -0.56f + pose.rightFootLift, 0.08f + pose.rightFootForward))
        val leftFootAnkle = project(bodyToWorld(Vec3(-0.15f, -0.44f + pose.leftFootLift, 0.02f + pose.leftFootForward * 0.6f)))
        val rightFootAnkle = project(bodyToWorld(Vec3(0.15f, -0.44f + pose.rightFootLift, 0.02f + pose.rightFootForward * 0.6f)))
        val leftFootP = project(leftFootWorld)
        val rightFootP = project(rightFootWorld)
        val footFwdDir = bodyDirToView(Vec3(0f, 0f, 1f))

        drawQueue.add(DrawCommand(leftFootP.camZ - 0.04f) {
            drawGoldenFoot(leftFootAnkle, leftFootP, footFwdDir)
        })
        drawQueue.add(DrawCommand(rightFootP.camZ - 0.04f) {
            drawGoldenFoot(rightFootAnkle, rightFootP, footFwdDir)
        })

        // 5. Torso Core, Cream-White Chest & Stylish Black-and-Gold Jacket with Waveform Emblem
        val torsoCenterP = project(bodyToWorld(Vec3(0f, -0.08f, 0f)))
        val torsoForwardView = bodyDirToView(Vec3(0f, 0f, 1f))
        val torsoRightView = bodyDirToView(Vec3(1f, 0f, 0f))
        val chestEmblemP = project(bodyToWorld(Vec3(0f, -0.11f, 0.36f)))
        val backHoodP = project(bodyToWorld(Vec3(0f, 0.14f, -0.34f)))
        val neckP = project(bodyToWorld(Vec3(0f, 0.25f, 0.08f)))

        outHitZones.add(
            HitZoneProjection(
                zone = MayurTouchZone.BODY,
                screenX = torsoCenterP.sx,
                screenY = torsoCenterP.sy,
                radiusPx = torsoCenterP.scale * 0.44f,
                camZ = torsoCenterP.camZ
            )
        )

        drawQueue.add(DrawCommand(torsoCenterP.camZ) {
            drawTorsoAndBlackGoldJacket(
                center = torsoCenterP,
                chestEmblem = chestEmblemP,
                backHood = backHoodP,
                torsoForwardZ = torsoForwardView.z,
                torsoRightX = torsoRightView.x,
                pose = pose,
                costume = costume
            )
        })

        // 3D Neck accessory (scarf or festive garland)
        if (costume == MayurCostume.COLD ||
            costume == MayurCostume.WIND ||
            costume == MayurCostume.SNOWFALL ||
            costume.isFestival
        ) {
            drawQueue.add(DrawCommand(neckP.camZ + 0.06f) {
                Mayur3DCostumeOverlay.drawNeckAccessory(this, costume, neckP, neckP.scale)
            })
        }

        // 6. Articulated 3D Fluffy Royal-Blue Wings (with black/gold jacket cuffs)
        fun queueWing(isLeft: Boolean) {
            val side = if (isLeft) -1f else 1f
            val flapDeg = if (isLeft) pose.leftWingFlapDeg else pose.rightWingFlapDeg
            val pitchDeg = if (isLeft) pose.leftWingPitchDeg else pose.rightWingPitchDeg
            val shoulderLocal = Vec3(side * 0.34f, 0.08f, -0.02f)
            val tipRel = Mayur3DMath.rotateEuler(
                Vec3(side * 0.20f, -0.32f, -0.04f),
                pitchDeg = -pitchDeg,
                yawDeg = side * 10f,
                rollDeg = -side * flapDeg
            )
            val shoulderP = project(bodyToWorld(shoulderLocal))
            val tipP = project(bodyToWorld(shoulderLocal + tipRel))
            val wingSortZ = shoulderP.camZ * 0.45f + tipP.camZ * 0.55f
            drawQueue.add(DrawCommand(wingSortZ) {
                drawArticulatedWing(shoulderP, tipP, side)
            })
        }
        queueWing(isLeft = true)
        queueWing(isLeft = false)

        // 7. 3D Fluffy Royal-Blue Head + Cream-White Face + Turquoise-Blue Eyes + Golden-Orange Beak
        val headCenterP = project(headToWorld(Vec3(0f, 0.16f, 0f)))
        val headForwardView = headDirToView(Vec3(0f, 0f, 1f))
        val headUpView = headDirToView(Vec3(0f, 1f, 0f))
        val headRightView = headDirToView(Vec3(1f, 0f, 0f))

        outHitZones.add(
            HitZoneProjection(
                zone = MayurTouchZone.HEAD,
                screenX = headCenterP.sx,
                screenY = headCenterP.sy,
                radiusPx = headCenterP.scale * 0.42f,
                camZ = headCenterP.camZ + 0.02f
            )
        )

        // Head base sphere + fluffy plumage + cream face mask
        drawQueue.add(DrawCommand(headCenterP.camZ) {
            drawPlushHeadAndFace(
                headCenter = headCenterP,
                headForward = headForwardView,
                headUp = headUpView,
                headRight = headRightView,
                pose = pose,
                costume = costume
            )
        })

        // Left & right 3D turquoise-blue eyes (culled naturally when rotated to back view)
        val leftEyeP = project(headToWorld(Vec3(-0.165f, 0.18f, 0.34f)))
        val rightEyeP = project(headToWorld(Vec3(0.165f, 0.18f, 0.34f)))
        val leftEyeNormal = headDirToView(Vec3(-0.28f, 0.04f, 0.96f))
        val rightEyeNormal = headDirToView(Vec3(0.28f, 0.04f, 0.96f))

        if (leftEyeNormal.z > 0.05f) {
            drawQueue.add(DrawCommand(leftEyeP.camZ + 0.04f) {
                drawExpressiveTurquoiseEye(
                    center = leftEyeP,
                    normalZ = leftEyeNormal.z,
                    isLeft = true,
                    pose = pose
                )
            })
        }
        if (rightEyeNormal.z > 0.05f) {
            drawQueue.add(DrawCommand(rightEyeP.camZ + 0.04f) {
                drawExpressiveTurquoiseEye(
                    center = rightEyeP,
                    normalZ = rightEyeNormal.z,
                    isLeft = false,
                    pose = pose
                )
            })
        }

        // Head-locked 3D golden sunglasses when Hot Sunny costume is active
        if (costume == MayurCostume.HOT_SUNNY && headForwardView.z > 0.14f) {
            val shadesZ = maxOf(leftEyeP.camZ, rightEyeP.camZ) + 0.08f
            drawQueue.add(DrawCommand(shadesZ) {
                Mayur3DCostumeOverlay.drawHeadLockedSunglasses(
                    scope = this,
                    leftEye = leftEyeP,
                    rightEye = rightEyeP,
                    headForwardZ = headForwardView.z,
                    scale = headCenterP.scale
                )
            })
        }

        // 3D Sculpted Golden-Orange Beak (protrudes naturally in side & 3/4 views)
        val beakBaseP = project(headToWorld(Vec3(0f, 0.10f, 0.35f)))
        val beakUpperTipP = project(headToWorld(Vec3(0f, 0.075f, 0.50f)))
        val beakLowerTipP = project(headToWorld(Vec3(0f, 0.038f - pose.beakOpenAmount * 0.045f, 0.45f)))
        if (headForwardView.z > -0.35f) {
            drawQueue.add(DrawCommand(maxOf(beakBaseP.camZ, beakUpperTipP.camZ) + 0.03f) {
                drawSculptedGoldenBeak(beakBaseP, beakUpperTipP, beakLowerTipP, pose.beakOpenAmount)
            })
        }

        // 8. Five Elegant Golden Peacock Crown Feathers (Aigrette) with secondary inertia
        val crownRootLocal = Vec3(0f, 0.50f, 0.02f)
        val crownCount = if (quality == MayurAnimationQuality.BATTERY_SAVER) 3 else 5
        for (c in 0 until crownCount) {
            val frac = if (crownCount <= 1) 0f else (c / (crownCount - 1f)) * 2f - 1f // -1..+1
            val tipHeight = 0.40f - abs(frac) * 0.08f
            val tipRel = Mayur3DMath.rotateEuler(
                Vec3(frac * 0.25f, tipHeight, -0.04f - abs(frac) * 0.04f),
                pitchDeg = pose.crownLagPitchDeg,
                yawDeg = 0f,
                rollDeg = pose.crownLagRollDeg + sin(pose.elapsedTimeSec * 2.8f + c) * 2.5f
            )
            val rootP = project(headToWorld(crownRootLocal))
            val tipP = project(headToWorld(crownRootLocal + tipRel))
            drawQueue.add(DrawCommand(tipP.camZ) {
                drawGoldenCrownFeather(rootP, tipP)
            })
        }

        // 9. Premium Black & Gold Headphones with MAYUR Music Logo
        // 3D Curved Headband arch (9 segments so depth wraps cleanly over the head)
        val bandSegments = 8
        for (s in 0 until bandSegments) {
            val a0 = PI * (s.toDouble() / bandSegments)
            val a1 = PI * ((s + 1).toDouble() / bandSegments)
            val p0Local = Vec3(-cos(a0).toFloat() * 0.41f, 0.16f + sin(a0).toFloat() * 0.37f, 0.02f)
            val p1Local = Vec3(-cos(a1).toFloat() * 0.41f, 0.16f + sin(a1).toFloat() * 0.37f, 0.02f)
            val p0 = project(headToWorld(p0Local))
            val p1 = project(headToWorld(p1Local))
            val segZ = (p0.camZ + p1.camZ) * 0.5f + 0.05f
            drawQueue.add(DrawCommand(segZ) {
                val strokeW = (p0.scale * 0.085f).coerceAtLeast(3.2f)
                // Matte black padded band
                drawLine(
                    color = Color(0xFF141824),
                    start = Offset(p0.sx, p0.sy),
                    end = Offset(p1.sx, p1.sy),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
                // Metallic gold outer accent rail
                drawLine(
                    color = Color(0xFFFFCA3A),
                    start = Offset(p0.sx, p0.sy - strokeW * 0.18f),
                    end = Offset(p1.sx, p1.sy - strokeW * 0.18f),
                    strokeWidth = strokeW * 0.30f,
                    cap = StrokeCap.Round
                )
            })
        }

        // Left & Right 3D Black-and-Gold Earcups with MAYUR Music Logo
        fun queueHeadphoneCup(isLeft: Boolean) {
            val side = if (isLeft) -1f else 1f
            val cupInnerP = project(headToWorld(Vec3(side * 0.38f, 0.15f, 0.02f)))
            val cupOuterP = project(headToWorld(Vec3(side * 0.46f, 0.15f, 0.02f)))
            val outwardNormal = headDirToView(Vec3(side, 0f, 0f))

            outHitZones.add(
                HitZoneProjection(
                    zone = MayurTouchZone.HEADPHONES,
                    screenX = cupOuterP.sx,
                    screenY = cupOuterP.sy,
                    radiusPx = cupOuterP.scale * 0.22f,
                    camZ = cupOuterP.camZ + 0.06f
                )
            )

            drawQueue.add(DrawCommand(cupOuterP.camZ + 0.05f) {
                drawMayurHeadphoneCup(
                    inner = cupInnerP,
                    outer = cupOuterP,
                    outwardNormalZ = outwardNormal.z,
                    beatPulse = pose.beatPulse,
                    waveformGlow = pose.waveformGlow
                )
            })
        }
        queueHeadphoneCup(isLeft = true)
        queueHeadphoneCup(isLeft = false)

        // 10. Painter's Algorithm Back-to-Front Depth Sort & Render!
        drawQueue.sortBy { it.camZ }
        for (i in drawQueue.indices) {
            drawQueue[i].draw(scope)
        }

        // 11. Foreground environment props, sleeping zZz & dance/reaction particles
        Mayur3DCostumeOverlay.drawForegroundEffects(scope, costume, pose, quality)
    }

    private fun DrawScope.drawPeacockTailFeather(
        base: ProjectedPoint,
        mid: ProjectedPoint,
        tip: ProjectedPoint,
        featherIndex: Int,
        quality: MayurAnimationQuality
    ) {
        val dx = tip.sx - base.sx
        val dy = tip.sy - base.sy
        val len = hypot(dx, dy).coerceAtLeast(4f)
        val angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        val plumeW = (tip.scale * 0.27f).coerceAtLeast(6f)
        val plumeH = (len * 0.48f).coerceAtLeast(8f)

        // Curved golden-emerald rachis shaft
        val shaft = Path().apply {
            moveTo(base.sx, base.sy)
            quadraticTo(mid.sx, mid.sy, tip.sx, tip.sy)
        }
        drawPath(
            path = shaft,
            color = Color(0xFF199E83),
            style = Stroke(width = (tip.scale * 0.032f).coerceAtLeast(1.4f), cap = StrokeCap.Round)
        )

        rotate(degrees = angleDeg + 90f, pivot = Offset(tip.sx, tip.sy)) {
            // Vivid emerald-to-cyan fluffy outer feather vane
            val vaneTopLeft = Offset(tip.sx - plumeW * 0.5f, tip.sy - plumeH * 0.36f)
            val vaneSize = Size(plumeW, plumeH)
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF00E5C3),
                        Color(0xFF00A87E),
                        Color(0xFF006B58)
                    ),
                    center = Offset(tip.sx, tip.sy + plumeH * 0.05f),
                    radius = maxOf(plumeW, plumeH) * 0.58f
                ),
                topLeft = vaneTopLeft,
                size = vaneSize
            )

            // Fine golden-cyan outer edge rim
            if (quality != MayurAnimationQuality.BATTERY_SAVER) {
                drawOval(
                    color = Color(0x8864FFDA),
                    topLeft = vaneTopLeft,
                    size = vaneSize,
                    style = Stroke(width = (tip.scale * 0.015f).coerceAtLeast(0.9f))
                )
            }

            // Golden eye halo (ocellus outer ring)
            val goldW = plumeW * 0.66f
            val goldH = plumeH * 0.52f
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFE066), Color(0xFFFFB300), Color(0xFFD98200)),
                    center = Offset(tip.sx, tip.sy),
                    radius = goldH * 0.55f
                ),
                topLeft = Offset(tip.sx - goldW * 0.5f, tip.sy - goldH * 0.52f),
                size = Size(goldW, goldH)
            )

            // Luminous cyan-turquoise mid ring
            val cyanW = goldW * 0.68f
            val cyanH = goldH * 0.66f
            drawOval(
                color = if (featherIndex % 2 == 0) Color(0xFF00D8FF) else Color(0xFF18E6D6),
                topLeft = Offset(tip.sx - cyanW * 0.5f, tip.sy - cyanH * 0.46f),
                size = Size(cyanW, cyanH)
            )

            // Deep royal-blue inner eye core
            val blueW = cyanW * 0.64f
            val blueH = cyanH * 0.64f
            drawOval(
                color = Color(0xFF0B2E8A),
                topLeft = Offset(tip.sx - blueW * 0.5f, tip.sy - blueH * 0.40f),
                size = Size(blueW, blueH)
            )

            // Crisp jewel specular dot
            drawCircle(
                color = Color(0xCCFFFFFF),
                radius = (blueW * 0.16f).coerceAtLeast(0.8f),
                center = Offset(tip.sx - blueW * 0.16f, tip.sy - blueH * 0.14f)
            )
        }
    }

    private fun DrawScope.drawGoldenFoot(
        ankle: ProjectedPoint,
        foot: ProjectedPoint,
        fwdDir: Vec3
    ) {
        val legW = (foot.scale * 0.055f).coerceAtLeast(2f)
        drawLine(
            color = Color(0xFFE58A1E),
            start = Offset(ankle.sx, ankle.sy),
            end = Offset(foot.sx, foot.sy),
            strokeWidth = legW,
            cap = StrokeCap.Round
        )

        val toeLen = foot.scale * 0.14f
        val fwdX = fwdDir.x * toeLen * 0.9f
        val fwdY = (0.35f - fwdDir.y * 0.25f) * toeLen * 0.65f
        for (toe in -1..1) {
            val spreadX = toe * toeLen * 0.52f
            val endX = foot.sx + fwdX + spreadX
            val endY = foot.sy + fwdY + abs(toe) * toeLen * 0.10f
            drawLine(
                color = Color(0xFFFFA726),
                start = Offset(foot.sx, foot.sy),
                end = Offset(endX, endY),
                strokeWidth = (legW * 0.95f).coerceAtLeast(2f),
                cap = StrokeCap.Round
            )
            // Warm sunlit highlight on top of toe
            drawCircle(
                color = Color(0xFFFFD180),
                radius = (legW * 0.35f).coerceAtLeast(1f),
                center = Offset(endX, endY - legW * 0.18f)
            )
        }
    }

    private fun DrawScope.drawTorsoAndBlackGoldJacket(
        center: ProjectedPoint,
        chestEmblem: ProjectedPoint,
        backHood: ProjectedPoint,
        torsoForwardZ: Float,
        torsoRightX: Float,
        pose: MayurBonePose,
        costume: MayurCostume
    ) {
        val rX = center.scale * 0.42f
        val rY = center.scale * 0.44f

        // 1. Fluffy royal-blue body base sphere with 3D studio shading
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF288CFF),
                    Color(0xFF0E4FB5),
                    Color(0xFF07235C)
                ),
                center = Offset(center.sx - rX * 0.24f, center.sy - rY * 0.26f),
                radius = maxOf(rX, rY) * 1.18f
            ),
            topLeft = Offset(center.sx - rX, center.sy - rY),
            size = Size(rX * 2f, rY * 2f)
        )

        // 2. Stylish Matte-Black Jacket Shell with Metallic Golden Trim
        val jacketTop = center.sy - rY * 0.62f
        val jacketH = rY * 1.48f
        val jacketW = rX * 2.04f
        val jacketTopLeft = Offset(center.sx - jacketW * 0.5f, jacketTop)
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF242938),
                    Color(0xFF10131C),
                    Color(0xFF080A10)
                ),
                start = Offset(center.sx - rX, jacketTop),
                end = Offset(center.sx + rX, jacketTop + jacketH)
            ),
            topLeft = jacketTopLeft,
            size = Size(jacketW, jacketH),
            cornerRadius = CornerRadius(rX * 0.72f, rY * 0.68f)
        )

        // Golden bottom hem trim wrapping around the jacket
        val trimStroke = (center.scale * 0.026f).coerceAtLeast(1.4f)
        drawArc(
            color = Color(0xFFFFCA3A),
            startAngle = 14f,
            sweepAngle = 152f,
            useCenter = false,
            topLeft = Offset(center.sx - jacketW * 0.47f, jacketTop + jacketH * 0.42f),
            size = Size(jacketW * 0.94f, jacketH * 0.56f),
            style = Stroke(width = trimStroke, cap = StrokeCap.Round)
        )

        // 3. Front-facing details: cream-white fluffy chest + golden collar lapels + glowing waveform emblem
        if (torsoForwardZ > -0.18f) {
            val frontVis = ((torsoForwardZ + 0.18f) / 0.45f).coerceIn(0f, 1f)
            val widthScale = torsoForwardZ.coerceIn(0.18f, 1f)
            val cx = chestEmblem.sx
            val chestY = center.sy - rY * 0.36f

            // Soft cream-white fluffy chest V-opening above jacket zip
            val creamW = rX * 0.68f * widthScale
            val creamH = rY * 0.56f
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFFAF0).copy(alpha = frontVis),
                        Color(0xFFF3E2C7).copy(alpha = frontVis)
                    ),
                    center = Offset(cx, chestY - creamH * 0.1f),
                    radius = maxOf(creamW, creamH) * 0.85f
                ),
                topLeft = Offset(cx - creamW * 0.5f, chestY - creamH * 0.45f),
                size = Size(creamW, creamH)
            )

            // Golden jacket V-collar piping framing the cream chest
            val lapelPath = Path().apply {
                moveTo(cx - creamW * 0.55f, chestY - creamH * 0.32f)
                lineTo(cx, chestY + creamH * 0.52f)
                lineTo(cx + creamW * 0.55f, chestY - creamH * 0.32f)
            }
            drawPath(
                path = lapelPath,
                color = Color(0xFFFFD54F).copy(alpha = frontVis),
                style = Stroke(width = trimStroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Glowing Golden Audio Waveform Emblem on Jacket Chest (live music-reactive!)
            if (torsoForwardZ > 0.08f) {
                val emAlpha = ((torsoForwardZ - 0.08f) / 0.35f).coerceIn(0f, 1f)
                val emScaleX = torsoForwardZ.coerceIn(0.25f, 1f)
                val glowRadius = center.scale * (0.19f + pose.waveformGlow * 0.08f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFD54F).copy(alpha = 0.46f * pose.waveformGlow * emAlpha),
                            Color.Transparent
                        ),
                        center = Offset(chestEmblem.sx, chestEmblem.sy + rY * 0.14f),
                        radius = glowRadius
                    ),
                    radius = glowRadius,
                    center = Offset(chestEmblem.sx, chestEmblem.sy + rY * 0.14f)
                )

                val barHeights = floatArrayOf(0.42f, 0.78f, 1.0f, 0.74f, 0.46f)
                val barSpacing = center.scale * 0.052f * emScaleX
                val barWidth = (center.scale * 0.025f * emScaleX).coerceAtLeast(1.3f)
                val baseBarMaxH = center.scale * (0.14f + pose.audioAmplitude * 0.06f + pose.beatPulse * 0.05f)
                val emY = chestEmblem.sy + rY * 0.14f

                for (b in barHeights.indices) {
                    val dynamicFactor = if (pose.isDancing) {
                        0.72f + 0.42f * abs(sin(pose.elapsedTimeSec * 9f + b * 1.15f)) * (0.5f + pose.audioAmplitude)
                    } else {
                        0.85f + 0.15f * sin(pose.elapsedTimeSec * 2.5f + b * 0.8f)
                    }
                    val halfH = baseBarMaxH * barHeights[b] * dynamicFactor * 0.5f
                    val bx = chestEmblem.sx + (b - 2) * barSpacing
                    drawLine(
                        color = Color(0xFFFFE066).copy(alpha = (0.72f + pose.waveformGlow * 0.28f) * emAlpha),
                        start = Offset(bx, emY - halfH),
                        end = Offset(bx, emY + halfH),
                        strokeWidth = barWidth,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // 4. Back-facing details: 3D sculpted folded matte-black hoodie hood & golden back emblem!
        if (torsoForwardZ < 0.25f) {
            val backVis = ((0.25f - torsoForwardZ) / 0.55f).coerceIn(0f, 1f)
            val hoodW = rX * 1.28f
            val hoodH = rY * 0.66f
            val hoodPath = Path().apply {
                moveTo(backHood.sx - hoodW * 0.5f, backHood.sy - hoodH * 0.35f)
                quadraticTo(backHood.sx, backHood.sy + hoodH * 0.72f, backHood.sx + hoodW * 0.5f, backHood.sy - hoodH * 0.35f)
                quadraticTo(backHood.sx, backHood.sy - hoodH * 0.05f, backHood.sx - hoodW * 0.5f, backHood.sy - hoodH * 0.35f)
                close()
            }
            drawPath(hoodPath, Color(0xFF1A1E2B).copy(alpha = backVis))
            drawPath(
                hoodPath,
                Color(0xFFFFCA3A).copy(alpha = 0.9f * backVis),
                style = Stroke(width = trimStroke, join = StrokeJoin.Round)
            )
            // Subtle golden back waveform chevron
            if (torsoForwardZ < -0.25f) {
                val chevY = center.sy + rY * 0.12f
                drawLine(
                    color = Color(0xFFFFCA3A).copy(alpha = 0.72f * backVis),
                    start = Offset(backHood.sx - rX * 0.28f, chevY),
                    end = Offset(backHood.sx + rX * 0.28f, chevY),
                    strokeWidth = trimStroke * 0.85f,
                    cap = StrokeCap.Round
                )
            }
        }

        // Festival Dussehra saffron-gold sash across jacket
        if (costume == MayurCostume.FESTIVAL_DUSSEHRA && torsoForwardZ > 0f) {
            drawLine(
                color = Color(0xFFFF7043).copy(alpha = 0.85f),
                start = Offset(center.sx - rX * 0.55f, center.sy - rY * 0.45f),
                end = Offset(center.sx + rX * 0.48f, center.sy + rY * 0.42f),
                strokeWidth = (center.scale * 0.06f).coerceAtLeast(2.4f),
                cap = StrokeCap.Round
            )
        }

        // Subtle side-silhouette metallic rim highlight
        val rimOffsetX = torsoRightX * rX * 0.05f
        drawOval(
            color = Color(0x337CE8FF),
            topLeft = Offset(center.sx - rX + rimOffsetX, center.sy - rY),
            size = Size(rX * 2f, rY * 2f),
            style = Stroke(width = (center.scale * 0.015f).coerceAtLeast(1f))
        )
    }

    private fun DrawScope.drawArticulatedWing(
        shoulder: ProjectedPoint,
        tip: ProjectedPoint,
        sideSign: Float
    ) {
        val dx = tip.sx - shoulder.sx
        val dy = tip.sy - shoulder.sy
        val len = hypot(dx, dy).coerceAtLeast(8f)
        val angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        val wingW = (shoulder.scale * 0.22f).coerceAtLeast(5f)

        rotate(degrees = angleDeg - 90f, pivot = Offset(shoulder.sx, shoulder.sy)) {
            // Black & gold jacket sleeve shoulder cuff
            drawRoundRect(
                color = Color(0xFF151924),
                topLeft = Offset(shoulder.sx - wingW * 0.56f, shoulder.sy - wingW * 0.25f),
                size = Size(wingW * 1.12f, len * 0.48f),
                cornerRadius = CornerRadius(wingW * 0.45f, wingW * 0.45f)
            )
            drawLine(
                color = Color(0xFFFFCA3A),
                start = Offset(shoulder.sx - wingW * 0.48f, shoulder.sy + len * 0.20f),
                end = Offset(shoulder.sx + wingW * 0.48f, shoulder.sy + len * 0.20f),
                strokeWidth = (shoulder.scale * 0.022f).coerceAtLeast(1.2f),
                cap = StrokeCap.Round
            )

            // Fluffy royal-blue to turquoise wing feathers emerging from sleeve
            drawOval(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF1D7AF2), Color(0xFF0A43A8), Color(0xFF00C4CC)),
                    startY = shoulder.sy + len * 0.16f,
                    endY = shoulder.sy + len * 1.15f
                ),
                topLeft = Offset(shoulder.sx - wingW * 0.46f, shoulder.sy + len * 0.18f),
                size = Size(wingW * 0.92f, len * 0.92f)
            )
            // Soft feather tip shimmer
            drawCircle(
                color = Color(0x994DD0E1),
                radius = wingW * 0.22f,
                center = Offset(shoulder.sx + sideSign * wingW * 0.08f, shoulder.sy + len * 0.96f)
            )
        }
    }

    private fun DrawScope.drawPlushHeadAndFace(
        headCenter: ProjectedPoint,
        headForward: Vec3,
        headUp: Vec3,
        headRight: Vec3,
        pose: MayurBonePose,
        costume: MayurCostume
    ) {
        val rX = headCenter.scale * 0.43f
        val rY = headCenter.scale * 0.39f

        // 1. Volumetric 3D Royal-Blue Plush Head Sphere (key light top-left, rim cyan-gold)
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF39A0FF),
                    Color(0xFF1565D8),
                    Color(0xFF0A2E80),
                    Color(0xFF061B52)
                ),
                center = Offset(headCenter.sx - rX * 0.28f, headCenter.sy - rY * 0.32f),
                radius = maxOf(rX, rY) * 1.24f
            ),
            topLeft = Offset(headCenter.sx - rX, headCenter.sy - rY),
            size = Size(rX * 2f, rY * 2f)
        )

        // Subtle fluffy plumage cheek & crown tufts around silhouette
        val tuftColor = Color(0xFF217DF0)
        drawCircle(tuftColor, radius = rX * 0.22f, center = Offset(headCenter.sx - rX * 0.72f, headCenter.sy + rY * 0.22f))
        drawCircle(tuftColor, radius = rX * 0.22f, center = Offset(headCenter.sx + rX * 0.72f, headCenter.sy + rY * 0.22f))

        // 2. Cream-White Plush Face & Cheek Mask (wrapped smoothly in 3D as head turns)
        if (headForward.z > -0.12f) {
            val faceVis = ((headForward.z + 0.12f) / 0.40f).coerceIn(0f, 1f)
            val faceShiftX = headForward.x * rX * 0.46f
            val faceShiftY = -headForward.y * rY * 0.35f + rY * 0.14f
            val faceWidthScale = (0.42f + headForward.z.coerceIn(0f, 1f) * 0.58f)

            val maskW = rX * 1.54f * faceWidthScale
            val maskH = rY * 1.24f
            val maskCenter = Offset(headCenter.sx + faceShiftX, headCenter.sy + faceShiftY)

            // Main plush cream-white lower face & cheeks
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFFDF7).copy(alpha = faceVis),
                        Color(0xFFFFF3DF).copy(alpha = faceVis),
                        Color(0xFFEAD4B5).copy(alpha = 0.92f * faceVis)
                    ),
                    center = Offset(maskCenter.x, maskCenter.y - maskH * 0.1f),
                    radius = maxOf(maskW, maskH) * 0.65f
                ),
                topLeft = Offset(maskCenter.x - maskW * 0.5f, maskCenter.y - maskH * 0.5f),
                size = Size(maskW, maskH)
            )

            // Upper eye-brow cream feather arches giving adorable peacock face contour
            val browW = maskW * 0.46f
            val browH = maskH * 0.48f
            drawOval(
                color = Color(0xFFFFF8EC).copy(alpha = faceVis),
                topLeft = Offset(maskCenter.x - maskW * 0.44f, maskCenter.y - maskH * 0.64f),
                size = Size(browW, browH)
            )
            drawOval(
                color = Color(0xFFFFF8EC).copy(alpha = faceVis),
                topLeft = Offset(maskCenter.x + maskW * 0.44f - browW, maskCenter.y - maskH * 0.64f),
                size = Size(browW, browH)
            )

            // Royal blue forehead V-crest feather emblem + golden filigree dot
            val crestX = headCenter.sx + headForward.x * rX * 0.42f
            val crestY = headCenter.sy - rY * 0.52f - headForward.y * rY * 0.25f
            val vPath = Path().apply {
                moveTo(crestX - rX * 0.22f * faceWidthScale, crestY - rY * 0.14f)
                lineTo(crestX, crestY + rY * 0.22f)
                lineTo(crestX + rX * 0.22f * faceWidthScale, crestY - rY * 0.14f)
                close()
            }
            drawPath(vPath, Color(0xFF0E4EB8).copy(alpha = faceVis))
            drawCircle(
                color = if (costume == MayurCostume.FESTIVAL_RATH_YATRA) Color(0xFFFF5722) else Color(0xFFFFD54F),
                radius = (rX * 0.085f).coerceAtLeast(1.8f),
                center = Offset(crestX, crestY - rY * 0.02f)
            )

            // Soft rosy cheek blush (plus Holi gulal tint during Holi!)
            val blushColor = if (costume == MayurCostume.FESTIVAL_HOLI) Color(0xFFEC407A) else Color(0xFFFF8AA8)
            val blushR = rX * 0.19f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(blushColor.copy(alpha = pose.blushAlpha * faceVis), Color.Transparent),
                    center = Offset(maskCenter.x - maskW * 0.32f, maskCenter.y + maskH * 0.12f),
                    radius = blushR
                ),
                radius = blushR,
                center = Offset(maskCenter.x - maskW * 0.32f, maskCenter.y + maskH * 0.12f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(blushColor.copy(alpha = pose.blushAlpha * faceVis), Color.Transparent),
                    center = Offset(maskCenter.x + maskW * 0.32f, maskCenter.y + maskH * 0.12f),
                    radius = blushR
                ),
                radius = blushR,
                center = Offset(maskCenter.x + maskW * 0.32f, maskCenter.y + maskH * 0.12f)
            )
        }

        // 3. Nighttime cozy sleeping cap perched on head top
        if (costume == MayurCostume.NIGHTTIME) {
            val capY = headCenter.sy - rY * 0.85f
            val capPath = Path().apply {
                moveTo(headCenter.sx - rX * 0.55f, capY + rY * 0.22f)
                quadraticTo(headCenter.sx, capY - rY * 0.55f, headCenter.sx + rX * 0.82f, capY + rY * 0.14f)
                quadraticTo(headCenter.sx + rX * 0.25f, capY + rY * 0.34f, headCenter.sx - rX * 0.55f, capY + rY * 0.22f)
                close()
            }
            drawPath(capPath, Color(0xFF1B2868))
            drawPath(capPath, Color(0xFFFFD54F), style = Stroke(width = (rX * 0.05f).coerceAtLeast(1.2f)))
            drawCircle(Color(0xFFFFE082), radius = rX * 0.14f, center = Offset(headCenter.sx + rX * 0.84f, capY + rY * 0.14f))
        }

        // Crisp cyan-sky 3D rim light on top-right of head
        val rimShiftX = headRight.x * rX * 0.04f
        val rimShiftY = -headUp.y * rY * 0.04f
        drawArc(
            color = Color(0x5580E8FF),
            startAngle = -140f,
            sweepAngle = 110f,
            useCenter = false,
            topLeft = Offset(headCenter.sx - rX + rimShiftX, headCenter.sy - rY + rimShiftY),
            size = Size(rX * 2f, rY * 2f),
            style = Stroke(width = (headCenter.scale * 0.018f).coerceAtLeast(1.1f), cap = StrokeCap.Round)
        )
    }

    private fun DrawScope.drawExpressiveTurquoiseEye(
        center: ProjectedPoint,
        normalZ: Float,
        isLeft: Boolean,
        pose: MayurBonePose
    ) {
        val foreshortenX = (0.36f + normalZ.coerceIn(0f, 1f) * 0.64f)
        val eyeRx = center.scale * 0.118f * foreshortenX
        val eyeRy = center.scale * 0.132f

        // Happy squinting arc or sleeping closed arc
        val isHappySquint = !pose.isSleeping && pose.smileAmount >= 0.82f && pose.blinkAmount >= 0.45f
        val isClosed = pose.blinkAmount >= 0.82f

        if (isHappySquint || isClosed) {
            val lidStroke = (center.scale * 0.026f).coerceAtLeast(1.8f)
            if (pose.isSleeping) {
                // Peaceful downward-curved sleeping eyelid
                drawArc(
                    color = Color(0xFF0A1633),
                    startAngle = 18f,
                    sweepAngle = 144f,
                    useCenter = false,
                    topLeft = Offset(center.sx - eyeRx, center.sy - eyeRy * 0.55f),
                    size = Size(eyeRx * 2f, eyeRy * 1.1f),
                    style = Stroke(width = lidStroke, cap = StrokeCap.Round)
                )
            } else {
                // Adorable joyful upward-curved smiling eye
                drawArc(
                    color = Color(0xFF0A1633),
                    startAngle = 195f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(center.sx - eyeRx, center.sy - eyeRy * 0.45f),
                    size = Size(eyeRx * 2f, eyeRy * 1.15f),
                    style = Stroke(width = lidStroke, cap = StrokeCap.Round)
                )
            }
            return
        }

        // Open glossy 3D turquoise-blue eye!
        val openFactor = (1f - pose.blinkAmount * 0.85f).coerceIn(0.18f, 1f)
        val currentRy = eyeRy * openFactor

        // 1. Dark limbal sclera contour
        drawOval(
            color = Color(0xFF08142B),
            topLeft = Offset(center.sx - eyeRx * 1.06f, center.sy - currentRy * 1.06f),
            size = Size(eyeRx * 2.12f, currentRy * 2.12f)
        )

        // 2. Radiant layered turquoise-blue iris
        val gazeX = pose.eyeLookX * eyeRx * 0.24f
        val gazeY = -pose.eyeLookY * currentRy * 0.20f
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF3BF7FF),
                    Color(0xFF00BDD6),
                    Color(0xFF005C99),
                    Color(0xFF081B4B)
                ),
                center = Offset(center.sx + gazeX, center.sy + gazeY + currentRy * 0.16f),
                radius = maxOf(eyeRx, currentRy) * 1.08f
            ),
            topLeft = Offset(center.sx - eyeRx * 0.92f, center.sy - currentRy * 0.92f),
            size = Size(eyeRx * 1.84f, currentRy * 1.84f)
        )

        // 3. Glossy deep pupil tracking user gaze
        val pupilRx = eyeRx * 0.48f
        val pupilRy = currentRy * 0.50f
        drawOval(
            color = Color(0xFF040B1C),
            topLeft = Offset(center.sx + gazeX - pupilRx, center.sy + gazeY - pupilRy),
            size = Size(pupilRx * 2f, pupilRy * 2f)
        )

        // 4. Lower turquoise luminescent crescent bounce
        drawArc(
            color = Color(0xAA6EFFFF),
            startAngle = 25f,
            sweepAngle = 130f,
            useCenter = false,
            topLeft = Offset(center.sx - eyeRx * 0.72f, center.sy - currentRy * 0.62f),
            size = Size(eyeRx * 1.44f, currentRy * 1.38f),
            style = Stroke(width = (center.scale * 0.018f).coerceAtLeast(1.1f), cap = StrokeCap.Round)
        )

        // 5. Twin Pixar-style crisp white specular catchlights
        drawCircle(
            color = Color.White,
            radius = (minOf(eyeRx, currentRy) * 0.30f).coerceAtLeast(1.4f),
            center = Offset(center.sx - eyeRx * 0.28f + gazeX * 0.3f, center.sy - currentRy * 0.30f + gazeY * 0.3f)
        )
        drawCircle(
            color = Color(0xDDFFFFFF),
            radius = (minOf(eyeRx, currentRy) * 0.14f).coerceAtLeast(0.8f),
            center = Offset(center.sx + eyeRx * 0.30f + gazeX * 0.3f, center.sy + currentRy * 0.26f)
        )

        // 6. Delicate upper eyelash wing
        val lashSign = if (isLeft) -1f else 1f
        drawLine(
            color = Color(0xFF08142B),
            start = Offset(center.sx + lashSign * eyeRx * 0.85f, center.sy - currentRy * 0.48f),
            end = Offset(center.sx + lashSign * eyeRx * 1.20f, center.sy - currentRy * 0.72f),
            strokeWidth = (center.scale * 0.018f).coerceAtLeast(1.2f),
            cap = StrokeCap.Round
        )
    }

    private fun DrawScope.drawSculptedGoldenBeak(
        base: ProjectedPoint,
        upperTip: ProjectedPoint,
        lowerTip: ProjectedPoint,
        openAmount: Float
    ) {
        val halfW = base.scale * 0.095f

        // Lower beak (visible when chirping or happy)
        val lowerPath = Path().apply {
            moveTo(base.sx - halfW * 0.72f, base.sy + base.scale * 0.015f)
            lineTo(lowerTip.sx, lowerTip.sy)
            lineTo(base.sx + halfW * 0.72f, base.sy + base.scale * 0.015f)
            close()
        }
        drawPath(lowerPath, Color(0xFFD97706))

        if (openAmount > 0.12f) {
            // Cute warm coral inner mouth peek when singing/excited
            val mouthPath = Path().apply {
                moveTo(base.sx - halfW * 0.48f, base.sy + base.scale * 0.01f)
                lineTo((upperTip.sx + lowerTip.sx) * 0.5f, (upperTip.sy + lowerTip.sy) * 0.52f)
                lineTo(base.sx + halfW * 0.48f, base.sy + base.scale * 0.01f)
                close()
            }
            drawPath(mouthPath, Color(0xFFE5536A))
        }

        // Sculpted golden-orange upper beak
        val upperPath = Path().apply {
            moveTo(base.sx - halfW, base.sy)
            quadraticTo(base.sx, base.sy - base.scale * 0.06f, base.sx + halfW, base.sy)
            quadraticTo((base.sx + upperTip.sx) * 0.5f + halfW * 0.25f, upperTip.sy, upperTip.sx, upperTip.sy)
            quadraticTo((base.sx + upperTip.sx) * 0.5f - halfW * 0.25f, upperTip.sy, base.sx - halfW, base.sy)
            close()
        }
        drawPath(
            path = upperPath,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFFD54F), Color(0xFFFF9800), Color(0xFFE65100)),
                start = Offset(base.sx, base.sy - base.scale * 0.06f),
                end = Offset(upperTip.sx, upperTip.sy + base.scale * 0.04f)
            )
        )
        // Specular highlight dot on beak ridge
        drawCircle(
            color = Color(0xBBFFF9C4),
            radius = (base.scale * 0.022f).coerceAtLeast(1f),
            center = Offset((base.sx + upperTip.sx) * 0.5f, (base.sy + upperTip.sy) * 0.5f - base.scale * 0.015f)
        )
    }

    private fun DrawScope.drawGoldenCrownFeather(
        root: ProjectedPoint,
        tip: ProjectedPoint
    ) {
        val midX = (root.sx + tip.sx) * 0.5f
        val midY = (root.sy + tip.sy) * 0.5f - root.scale * 0.04f
        val stem = Path().apply {
            moveTo(root.sx, root.sy)
            quadraticTo(midX, midY, tip.sx, tip.sy)
        }
        drawPath(
            path = stem,
            color = Color(0xFFFFCA3A),
            style = Stroke(width = (root.scale * 0.022f).coerceAtLeast(1.2f), cap = StrokeCap.Round)
        )

        val r = (tip.scale * 0.062f).coerceAtLeast(2.6f)
        // Golden tear bezel
        drawCircle(Color(0xFFFFCA3A), radius = r, center = Offset(tip.sx, tip.sy))
        // Turquoise inner ring
        drawCircle(Color(0xFF00E5FF), radius = r * 0.68f, center = Offset(tip.sx, tip.sy))
        // Royal blue jewel center
        drawCircle(Color(0xFF0D2F8B), radius = r * 0.42f, center = Offset(tip.sx, tip.sy))
    }

    private fun DrawScope.drawMayurHeadphoneCup(
        inner: ProjectedPoint,
        outer: ProjectedPoint,
        outwardNormalZ: Float,
        beatPulse: Float,
        waveformGlow: Float
    ) {
        val cupRy = outer.scale * 0.185f
        val foreshorten = (0.34f + abs(outwardNormalZ).coerceIn(0f, 1f) * 0.66f)
        val cupRx = cupRy * foreshorten

        // Soft ear cushion connecting inner & outer housing
        drawOval(
            color = Color(0xFF0B0E17),
            topLeft = Offset(inner.sx - cupRx * 0.92f, inner.sy - cupRy * 1.04f),
            size = Size(cupRx * 1.84f, cupRy * 2.08f)
        )

        // Outer metallic gold housing bevel
        drawOval(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFFE57F), Color(0xFFFFB300), Color(0xFFB26A00)),
                start = Offset(outer.sx - cupRx, outer.sy - cupRy),
                end = Offset(outer.sx + cupRx, outer.sy + cupRy)
            ),
            topLeft = Offset(outer.sx - cupRx, outer.sy - cupRy),
            size = Size(cupRx * 2f, cupRy * 2f)
        )

        // Matte-black recessed acoustic cap
        val capRx = cupRx * 0.80f
        val capRy = cupRy * 0.80f
        drawOval(
            color = Color(0xFF101420),
            topLeft = Offset(outer.sx - capRx, outer.sy - capRy),
            size = Size(capRx * 2f, capRy * 2f)
        )

        // Beat-reactive golden glow ring on earcup
        if (beatPulse > 0.05f || waveformGlow > 0.4f) {
            drawOval(
                color = Color(0xFFFFD54F).copy(alpha = (0.28f + beatPulse * 0.55f).coerceIn(0f, 0.85f)),
                topLeft = Offset(outer.sx - capRx * 0.88f, outer.sy - capRy * 0.88f),
                size = Size(capRx * 1.76f, capRy * 1.76f),
                style = Stroke(width = (outer.scale * 0.016f).coerceAtLeast(1.1f))
            )
        }

        // Iconic golden MAYUR Peacock-Vinyl Music Logo on visible outer earcup face!
        if (outwardNormalZ > 0.14f) {
            val logoAlpha = ((outwardNormalZ - 0.14f) / 0.35f).coerceIn(0f, 1f)
            val gold = Color(0xFFFFD54F).copy(alpha = logoAlpha)
            val stroke = Stroke(width = (outer.scale * 0.016f).coerceAtLeast(1.0f), cap = StrokeCap.Round)

            // Vinyl disc ring
            val vinylRx = capRx * 0.52f
            val vinylRy = capRy * 0.52f
            drawOval(
                color = gold,
                topLeft = Offset(outer.sx - vinylRx, outer.sy - vinylRy * 0.65f),
                size = Size(vinylRx * 2f, vinylRy * 1.65f),
                style = stroke
            )
            // Stylized golden peacock neck S-curve crest above vinyl center
            val crest = Path().apply {
                moveTo(outer.sx, outer.sy + vinylRy * 0.25f)
                quadraticTo(outer.sx + vinylRx * 0.45f, outer.sy - vinylRy * 0.10f, outer.sx - vinylRx * 0.10f, outer.sy - vinylRy * 0.55f)
                quadraticTo(outer.sx + vinylRx * 0.18f, outer.sy - vinylRy * 0.88f, outer.sx + vinylRx * 0.42f, outer.sy - vinylRy * 0.72f)
            }
            drawPath(crest, gold, style = stroke)
            // Center spindle dot
            drawCircle(gold, radius = (minOf(vinylRx, vinylRy) * 0.22f).coerceAtLeast(1.0f), center = Offset(outer.sx, outer.sy + vinylRy * 0.15f))
        }
    }
}
